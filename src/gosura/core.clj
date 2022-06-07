(ns gosura.core
  "GraphQL relay spec에 맞는 기본적인 resolver들을 자동 생성하려합니다.
   이에 필요한 schema, generator 등을 정의합니다
   edn 파일로 정의하지 않으면 자동생성되지 않으니 강제 사항은 아닙니다
   필요한 resolver만 자동 생성하고 custom하게 작성해야하는 경우는 따로 작성하면 됩니다

   주의) namespace를 임의로 지정하기 보다는 ns를 정의를 하고 해당 네임스페이스를 사용하기시 바랍니다(ns 선언 외 빈 파일)"
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.set :as s]
            [clojure.tools.logging :as log]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [failjure.core :as f]
            [gosura.auth :as auth]
            [gosura.helpers.relay :as relay]
            [gosura.helpers.resolver :as r]
            [gosura.util :as util]
            [gosura.schema :as schema]
            [malli.core :as m]
            [malli.error :as me]
            [medley.core :as medley]
            [util.resolver-helper :as rh]))

(defn- ->kebab-case
  [kebab-case? args parent]
  (if kebab-case?
    {:args   (util/transform-keys->kebab-case-keyword args)
     :parent (util/transform-keys->kebab-case-keyword parent)}
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
      (medley/update-existing :mutation-fn requiring-resolve)))

(defn match-resolve-fn
  "resolver-config로부터 전달 받은 resolver 값에 따라
   resolver-helper에서 정의한 적절한 resolver함수를 지정한다"
  [resolver]
  (try
    (condp re-matches (name resolver)
      #"resolve-connection" r/resolve-connection
      #"resolve-by-parent-pk-(.*)" r/resolve-by-parent-pk
      #"resolve-by-fk" r/resolve-by-fk
      #"resolve-by-fk-(.*)" r/resolve-by-fk
      #"resolve-connection-by-pk-list" r/resolve-connection-by-pk-list
      #"resolve-connection-by-(.*)" r/resolve-connection-by-fk
      #"resolve-create-one" r/resolve-create-one
      #"resolve-update-one" r/resolve-update-one
      #"resolve-delete-one" r/resolve-delete-one)
    (catch Exception e
      (f/fail (format "Resolver matching failed because of %s" (ex-message e))))))

(defn extract-auth-map
  [auth]
  (when (coll? auth)
    (let [[_auth-fn auth-map] auth]
      auth-map)))

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
  (let [{:keys [target-ns resolvers node-type db-key post-process-row pre-process-arguments]} resolver-config]
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
                                                #_{:clj-kondo/ignore [:unresolved-symbol]}
                                                (f/attempt-all
                                                 [{:keys [auth]} settings
                                                  auth-map (extract-auth-map auth)
                                                  auth-result (if-let [role (auth/process-auth-fn auth ctx)]
                                                                (if (boolean? role)
                                                                  role
                                                                  {(role auth-map) (get-in ctx [:identity :id])})
                                                                (f/fail "Unauthorized"))
                                                  filter-options (cond-> {:id (or (:db-id this)
                                                                                  (:id this))}
                                                                   (map? auth-map) (merge auth-result))
                                                  origin-content (table-fetcher (get ctx db-key) filter-options {})
                                                  _ (when (empty? origin-content)
                                                      (f/fail "NotExistData"))
                                                  content (->> origin-content
                                                               (map post-process-row)
                                                               (map (partial cske/transform-keys csk/->camelCaseKeyword))
                                                               first)]
                                                 (tag-with-type (relay/encode-node-id node-type content) (csk/->PascalCaseKeyword node-type))
                                                 (f/when-failed [e]
                                                                (resolve-as nil {:resolver (format "%s/%s" (str target-ns) (name resolver))
                                                                                 :message  (f/message e)})))))
          (intern target-ns (symbol resolver) (fn [ctx args parent]
                                                #_{:clj-kondo/ignore [:unresolved-symbol]}
                                                (f/attempt-all [{:keys [auth
                                                                        kebab-case?
                                                                        return-camel-case?]
                                                                 :or   {kebab-case?        true
                                                                        return-camel-case? true}} settings
                                                                {:keys [args parent]} (->kebab-case kebab-case? args parent)
                                                                auth-map (extract-auth-map auth)
                                                                auth-result (if-let [role (auth/process-auth-fn auth ctx)]
                                                                              (if (boolean? role)
                                                                                role
                                                                                {(role auth-map) (get-in ctx [:identity :id])})
                                                                              (f/fail "Unauthorized"))
                                                                required-keys-in-parent (remove nil? [fk-in-parent pk-list-name-in-parent])
                                                                required-keys (s/difference (set required-keys-in-parent) (set (keys parent)))
                                                                _ (when (seq required-keys)
                                                                    (f/fail (format "%s keys are needed in parent" required-keys)))
                                                                resolver-fn (match-resolve-fn resolver)
                                                                added-params (cond-> params
                                                                               (map? auth-map) (assoc :additional-filter-opts auth-result))]
                                                               (cond-> (resolver-fn ctx args parent added-params)
                                                                 return-camel-case? (rh/update-resolver-result transform-keys->camelCaseKeyword))
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
