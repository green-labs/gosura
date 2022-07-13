(ns gosura.core
  "GraphQL relay spec에 맞는 기본적인 resolver들을 자동 생성하려합니다.
   이에 필요한 schema, generator 등을 정의합니다
   edn 파일로 정의하지 않으면 자동생성되지 않으니 강제 사항은 아닙니다
   필요한 resolver만 자동 생성하고 custom하게 작성해야하는 경우는 따로 작성하면 됩니다

   주의) namespace를 임의로 지정하기 보다는 ns를 정의를 하고 해당 네임스페이스를 사용하기시 바랍니다(ns 선언 외 빈 파일)"
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as s]
            [clojure.tools.logging :as log]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [failjure.core :as f]
            [gosura.auth :as auth]
            [gosura.helpers.relay :as relay]
            [gosura.helpers.resolver :as r]
            [gosura.schema :as schema]
            [gosura.util :as util :refer [transform-keys->camelCaseKeyword
                                          transform-keys->kebab-case-keyword]]
            [malli.core :as m]
            [malli.error :as me]
            [medley.core :as medley]))

(defn- ->kebab-case
  [kebab-case? args parent]
  (if kebab-case?
    {:args   (transform-keys->kebab-case-keyword args)
     :parent (transform-keys->kebab-case-keyword parent)}
    {:args   args
     :parent parent}))

(defn- auth-symbol->requiring-var!
  [params]
  (cond-> params
    (symbol? (get-in params [:settings :auth 0])) (update-in [:settings :auth 0] requiring-resolve)
    (symbol? (get-in params [:settings :auth])) (update-in [:settings :auth] requiring-resolve)))

(defn- symbol->requiring-var!  "params: original params
   symbol로 들어온 값들 중에 var로 취급되어야하는 것들을 변환한다"
  [params]
  (-> params
      auth-symbol->requiring-var!
      (medley/update-existing :table-fetcher requiring-resolve)
      (medley/update-existing :pre-process-arguments requiring-resolve)
      (medley/update-existing :post-process-row requiring-resolve)
      (medley/update-existing :superfetcher requiring-resolve)
      (medley/update-existing :mutation-fn requiring-resolve)
      (medley/update-existing :fetch-one requiring-resolve)))

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
      #"resolve-one" r/resolve-one)
    (catch Exception e
      (f/fail (format "Can't find resolver-fn because of %s" (ex-message e))))))

(defn generate-one
  "GraphQL relay spec에 맞는 기본적인 resolver들을 자동 생성한다.
   제공하고 있는 resolvers의 종류는 아래와 같다.
   - resolve-connection: GraphQL relay spec에서 connection object를 조회할 때
   - resolve-by-fk: fk로 조회한 값
   - resolve-connection-by-xxx: xxx는 보통 fk로, xxx에 따른 connection object를 조회할 때 사용한다

   m: 설정값의 hash-map
   예시)
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
   "
  [resolver-config]
  (when-not (m/validate schema/resolvers-map-schema resolver-config)
    (throw (ex-info (str "resolvers 생성시 스키마가 맞지 않습니다: " (-> schema/resolvers-map-schema
                                                            (m/explain resolver-config)
                                                            me/humanize))
                    resolver-config)))
  (let [{:keys [target-ns resolvers node-type db-key post-process-row pre-process-arguments
                filters]} resolver-config]
    (when (nil? (find-ns target-ns)) (create-ns target-ns))
    (doseq [[resolver params] resolvers]
      (let [params (merge {:node-type        node-type
                           :db-key           db-key
                           :post-process-row (if (nil? post-process-row) identity (requiring-resolve post-process-row))
                           :pre-process-arguments (if (nil? pre-process-arguments) identity (requiring-resolve pre-process-arguments))}
                          (symbol->requiring-var! params))
            {:keys [table-fetcher node-type post-process-row db-key settings fk-in-parent pk-list-name-in-parent]} params]
        (if (= :resolve-node resolver)
          (intern target-ns (symbol resolver) (defmethod relay/node-resolver node-type [this ctx _args _parent]
                                                (f/attempt-all
                                                  [{:keys [auth]} settings
                                                   auth-filter-opts (auth/->auth-result auth ctx)
                                                   config-filter-opts (auth/config-filter-opts filters ctx)
                                                   filter-options (merge {:id (or (:db-id this)
                                                                                  (:id this))}
                                                                         auth-filter-opts
                                                                         config-filter-opts)
                                                   rows (table-fetcher (get ctx db-key) filter-options {})
                                                   _ (when (empty? rows)
                                                       (f/fail "NotExistData"))]
                                                  (-> (first rows)
                                                      (relay/build-node node-type post-process-row)
                                                      transform-keys->camelCaseKeyword
                                                      (tag-with-type (csk/->PascalCaseKeyword node-type)))
                                                  (f/when-failed [e]
                                                    (resolve-as nil {:resolver (format "%s/%s" (str target-ns) (name resolver))
                                                                     :message  (f/message e)})))))
          (intern target-ns (symbol resolver) (fn [ctx args parent]
                                                (f/attempt-all
                                                  [{:keys [auth
                                                           kebab-case?
                                                           return-camel-case?]
                                                    :or   {kebab-case?        true
                                                           return-camel-case? true}} settings
                                                   {:keys [args parent]} (->kebab-case kebab-case? args parent)
                                                   auth-filter-opts (auth/->auth-result auth ctx)
                                                   config-filter-opts (auth/config-filter-opts filters ctx)
                                                   required-keys-in-parent (remove nil? [fk-in-parent pk-list-name-in-parent])
                                                   required-keys (s/difference (set required-keys-in-parent) (set (keys parent)))
                                                   _ (when (seq required-keys)
                                                       (f/fail (format "%s keys are needed in parent" required-keys)))
                                                   resolver-fn (find-resolver-fn resolver)
                                                   added-params (merge params {:additional-filter-opts (merge auth-filter-opts
                                                                                                              config-filter-opts)})]
                                                  (cond-> (resolver-fn ctx args parent added-params)
                                                    return-camel-case? (util/update-resolver-result transform-keys->camelCaseKeyword))
                                                  (f/when-failed [e]
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
