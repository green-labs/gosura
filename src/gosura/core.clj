(ns gosura.core
  "relay.dev spec 의 Connection, Node 를 구현하는
   lacinia schema, resolver-fn 을 생성합니다.

   gosura resolver-config edn 파일을 정의하면 생성합니다.
   gosura 에서 생성하는 resolver-fn 이 부적합 할 때는 따로 작성하는 것이 더 적절할 수 있습니다.

   주의) resolver-config edn 에 사용하는 ns 는 (ns 선언만 있는 빈 파일을 만들고) 그 네임스페이스를 사용하세요."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as s]
            [clojure.tools.logging :as log]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [failjure.core :as f]
            [gosura.auth :as auth]
            [gosura.helpers.relay :as relay]
            [gosura.helpers.resolver :as r]
            [gosura.helpers.resolver2 :as r2]
            [gosura.schema :as schema]
            [gosura.util :as util :refer [requiring-var!
                                          transform-keys->kebab-case-keyword update-existing]]
            [malli.core :as m]
            [malli.error :as me]))

(defn-
  ^{:deprecated "0.2.8"}
  auth-symbol->requiring-var!
  [params]
  (cond-> params
    (symbol? (get-in params [:settings :auth 0])) (update-in [:settings :auth 0] requiring-var!)
    (symbol? (get-in params [:settings :auth])) (update-in [:settings :auth] requiring-var!)))

(defn-
  ^{:deprecated "0.2.8"}
  symbol->requiring-var!
  "params: original params
   symbol로 들어온 값들 중에 var로 취급되어야하는 것들을 변환한다"
  [params]
  (-> params
      auth-symbol->requiring-var!
      (update-existing :table-fetcher requiring-var!)
      (update-existing :pre-process-arguments requiring-var!)
      (update-existing :post-process-row requiring-var!)
      (update-existing :superfetcher requiring-var!)
      (update-existing :mutation-fn requiring-var!)
      (update-existing :fetch-one requiring-var!)))

(defn find-resolver-fn
  "resolver-key 에 따라 적절한 resolver-fn 함수를 리턴한다.

   보통 ns `gosura.helpers.resolver` 의 resolver-fn 를 리턴한다. "
  [resolver-key]
  {:pre [(keyword? resolver-key)]}
  (try
    (condp re-matches (name resolver-key)
      #"resolve-connection" r/resolve-connection
      #"resolve-by-parent-pk-(.*)" r/resolve-by-parent-pk
      #"resolve-by-fk" r/resolve-by-fk
      #"resolve-by-fk-(.*)" r/resolve-by-fk
      #"resolve-connection-by-pk-list" r/resolve-connection-by-pk-list
      #"resolve-connection-by-(.*)" r/resolve-connection-by-fk
      #"resolve-create-one" r/resolve-create-one
      #"resolve-update-one" r/resolve-update-one
      #"resolve-delete-one" r/resolve-delete-one
      #"resolve-update-multi" r/resolve-update-multi
      #"resolve-one" r/resolve-one

      ;; resolver2
      #"connection-by-(.*)" r2/connection-by
      #"one-by-(.*)" r2/one-by)
    (catch Exception e
      (f/fail (format "Can't find resolver-fn because of %s" (ex-message e))))))

(defn generate-one
  "gosura resolver-config edn 을 받아
   :target-ns 에 resolver-fn 을 생성(intern)합니다.

   gosura `resolver-config` edn 예)
   ```
   {:target-ns         ns
    :resolvers         {:resolve-connection               {:node-type             node-type
                                                           :db-key                db-key
                                                           :table-fetcher         superfetcher
                                                           :pre-process-arguments pre-process-arguments
                                                           :post-process-row      post-process-row}
                        :resolve-by-fk                    {:settings {:auth my-auth-fn}
                                                           :node-type         node-type
                                                           :superfetcher      superfetcher/->Fetch
                                                           :post-process-row  post-process-row
                                                           :fk-in-parent      :fk-in-parent}
                        :resolve-connection-by-example-id {:settings {:auth [my-auth-fn-2 #{my-role}]}
                                                           :node-type         node-type
                                                           :superfetcher      superfetcher/->FetchByExampleId
                                                           :post-process-row  post-process-row}}}
   ```
   "
  [resolver-config]
  (when-not (m/validate schema/resolvers-map-schema resolver-config)
    (throw (ex-info (str "resolvers 생성시 스키마가 맞지 않습니다: " (-> schema/resolvers-map-schema
                                                            (m/explain resolver-config)
                                                            me/humanize))
                    resolver-config)))
  (let [{:keys [target-ns resolvers node-type db-key post-process-row pre-process-arguments filters]} resolver-config]
    (when (nil? (find-ns target-ns)) (create-ns target-ns))
    (doseq [[resolver params] resolvers]
      (let [params (merge {:node-type             node-type
                           :db-key                db-key
                           :post-process-row      (if (nil? post-process-row) identity (requiring-var! post-process-row))
                           :pre-process-arguments (if (nil? pre-process-arguments) identity (requiring-var! pre-process-arguments))}
                          (symbol->requiring-var! params))
            {:keys [table-fetcher node-type post-process-row db-key settings fk-in-parent pk-list-name-in-parent]} params]
        (if (= :resolve-node resolver)
          (intern target-ns (symbol resolver) (defmethod relay/node-resolver node-type [this ctx _args _parent]
                                                (f/attempt-all
                                                  [{:keys [auth]} settings
                                                   auth-filter-opts (auth/->auth-result auth ctx)
                                                   config-filter-opts (auth/config-filter-opts filters ctx)
                                                   resolver-filter-opts (auth/config-filter-opts (:filters params) ctx)
                                                   filter-options (merge {:id (or (:db-id this)
                                                                                  (:id this))}
                                                                         auth-filter-opts
                                                                         config-filter-opts
                                                                         resolver-filter-opts)
                                                   rows (table-fetcher (get ctx db-key) filter-options {})
                                                   _ (when (empty? rows)
                                                       (f/fail "NotExistData"))]
                                                  (-> (first rows)
                                                      (relay/build-node node-type post-process-row)
                                                      (tag-with-type (csk/->PascalCaseKeyword node-type)))
                                                  (f/when-failed [e]
                                                    (log/error e)
                                                    (resolve-as nil {:resolver (format "%s/%s" (str target-ns) (name resolver))
                                                                     :message  (f/message e)})))))
          (intern target-ns (symbol resolver) (fn [ctx args parent]
                                                (f/attempt-all
                                                  [{:keys [auth
                                                           kebab-case?]
                                                    :or   {kebab-case? true}} settings
                                                   args' (if kebab-case? (transform-keys->kebab-case-keyword args) args)
                                                   auth-filter-opts (auth/->auth-result auth ctx)
                                                   config-filter-opts (auth/config-filter-opts filters ctx)
                                                   resolver-filter-opts (auth/config-filter-opts (:filters params) ctx)
                                                   required-keys-in-parent (remove nil? [fk-in-parent pk-list-name-in-parent])
                                                   required-keys (s/difference (set required-keys-in-parent) (set (keys parent)))
                                                   _ (when (seq required-keys)
                                                       (f/fail (format "%s keys are needed in parent" required-keys)))
                                                   resolver-fn (find-resolver-fn resolver)
                                                   added-params (merge params {:additional-filter-opts (merge auth-filter-opts
                                                                                                              config-filter-opts
                                                                                                              resolver-filter-opts)})]
                                                  (resolver-fn ctx args' parent added-params)
                                                  (f/when-failed [e]
                                                    (log/error e)
                                                    (resolve-as nil {:resolver (format "%s/%s" (str target-ns) (name resolver))
                                                                     :message  (f/message e)}))))))))

    (log/info (format "Gosura has generated resolvers => %s"
                      (mapv str
                            (repeat (str target-ns "/"))
                            (->> resolvers
                                 keys
                                 (map name)))))
    :generated))

(defn generate-all
  "GraphQL relay spec에 맞는 기본적인 resolver들을 여러개 동시에 생성한다.
   hash-map의 vector를 인자를 받으며 execute-one이 vector의 사이즈만큼 실행된다"
  [resolver-configs]
  (doseq [resolver-config resolver-configs]
    (generate-one resolver-config))
  :all-generated)
