(ns gosura.helpers.resolver2
  "gosura.helpers.resolver의 v2입니다."
  (:require [com.walmartlabs.lacinia.resolve :refer [resolve-as] :as resolve]
            [failjure.core :as f]
            [gosura.auth :as auth]
            [gosura.helpers.error :as error]
            [gosura.helpers.relay :as relay]
            [gosura.helpers.resolver :refer [common-pre-process-arguments
                                             keys-not-found
                                             nullify-empty-string-arguments parse-fdecl]]
            [gosura.helpers.superlifter :refer [with-superlifter]]
            [gosura.util :as util :refer [transform-keys->camelCaseKeyword
                                          transform-keys->kebab-case-keyword
                                          update-resolver-result]]
            [promesa.core :as prom]
            [superlifter.api :as superlifter-api]
            [taoensso.timbre :as log]))

(defmacro wrap-catch-body
  [catch-exceptions? body]
  (if catch-exceptions?
    `(try
       ~body
       (catch Exception e#
         (log/error e#)
         (resolve-as
          nil
          (error/error-response e#))))
    body))

(defn transform-body
  [body return-camel-case?]
  (cond-> body
    return-camel-case? (update-resolver-result transform-keys->camelCaseKeyword)))

(defmacro wrap-async-body
  [async? return-camel-case? body]
  (if async?
    `(let [result# (resolve/resolve-promise)]
       (prom/future
         (try
           (resolve/deliver! result# (transform-body ~body ~return-camel-case?))
           (catch Throwable t#
             (resolve/deliver! result# nil (error/error-response t#)))))
       result#)
    body))

(defmacro wrap-resolver-body
  "GraphQL 리졸버가 공통으로 해야 할 auth 처리, case 변환 처리를 resolver body의 앞뒤에서 해 주도록 wrapping합니다.

  this, ctx, arg, parent: 상위 리졸버 생성 매크로에서 만든 심벌
  option: 리졸버 선언에 지정된 옵션 맵
   - :auth - 인증함수를 넣습니다. gosura.auth의 설명을 참고해주세요.
   - :kebab-case? - arg/parent 의 key를 kebab-case로 변환할지 설정합니다. (기본값 true)
   - :return-camel-case? - 반환값을 camelCase 로 변환할지 설정합니다. (기본값 true)
   - :catch-exceptions? - 리졸버에서 발생하는 예외를 포착하여 :errors 응답으로 반환 (기본값 true)
   - :required-keys-in-parent - 부모(hash-map)로부터 필요한 required keys를 설정합니다.
   - :decode-ids-by-keys - 키 목록을 받아서 resolver args의 global id들을 db id로 변환 해줍니다.
   - :filters - args에 추가할 key-value 값을 필터로 넣습니다.
  "
  [{:keys [this ctx arg parent]} option args body]
  (let [{:keys [auth kebab-case? return-camel-case? required-keys-in-parent
                filters decode-ids-by-keys catch-exceptions? async?]
         :or   {kebab-case?             true
                return-camel-case?      true
                catch-exceptions?       true
                async?                  false
                required-keys-in-parent []}} option
        result (gensym 'result_)
        auth-filter-opts `(auth/->auth-result ~auth ~ctx)
        config-filter-opts `(auth/config-filter-opts ~filters ~ctx)
        arg `(merge ~arg ~auth-filter-opts ~config-filter-opts)
        arg' (if kebab-case? `(transform-keys->kebab-case-keyword ~arg) arg)
        arg' (if decode-ids-by-keys `(relay/decode-global-ids-by-keys ~arg' ~decode-ids-by-keys) arg')
        parent' (if kebab-case? `(transform-keys->kebab-case-keyword ~parent) parent)
        keys-not-found `(keys-not-found ~parent' ~required-keys-in-parent)
        params (if (nil? this) [ctx arg' parent'] [this ctx arg' parent'])
        let-mapping (vec (interleave args params))]
    `(if (seq ~keys-not-found)
       (error/error {:message (format "%s keys are needed in parent" ~keys-not-found)})
       (if (or (nil? ~auth-filter-opts)
               (and ~auth-filter-opts
                    (not (f/failed? ~auth-filter-opts))))
         (let [~result (do (let ~let-mapping (->> ~@body
                                                  (wrap-async-body ~async? ~return-camel-case?)
                                                  (wrap-catch-body ~catch-exceptions?))))]
           (transform-body ~result ~return-camel-case?))
         (resolve-as nil {:message "Unauthorized"})))))


(defmacro defresolver
  "lacinia 용 resolver 함수를 만듭니다.

  입력
  name - 함수 이름
  doc-string? - 문서
  option? - 설정
  args - 매개변수 [ctx arg parent]
  body - 함수 바디

  TODO: defn과 같이 attr-map을 받는 기능 추가

  가능한 설정
  :auth - 인증함수를 넣습니다. gosura.auth의 설명을 참고해주세요.
  :kebab-case? - arg/parent 의 key를 kebab-case로 변환할지 설정합니다. (기본값 true)
  :return-camel-case? - 반환값을 camelCase 로 변환할지 설정합니다. (기본값 true)
  :catch-exceptions? - 리졸버에서 발생하는 예외를 포착하여 :errors 응답으로 반환 (기본값 true)
  :required-keys-in-parent - 부모(hash-map)로부터 필요한 required keys를 설정합니다.
  :filters - 특정 필터 로직을 넣습니다"
  {:arglists '([name doc-string? option? args & body])}
  [name & fdecl]
  (let [{:keys [doc-string option args body]} (parse-fdecl fdecl)
        header                                (if doc-string [name doc-string] [name])]
    `(defn ~@header [ctx# arg# parent#]
       (wrap-resolver-body {:ctx    ctx#
                            :arg    arg#
                            :parent parent#} ~option ~args ~body))))

(defn connection-by
  "Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :superfetcher: 슈퍼페처
    * :post-process-row: 결과 객체 목록 후처리 함수 (예: identity)
    * :parent-id: 부모로부터 전달되는 id 정보 예) {:pre-fn relay/decode-global-id->db-id :prop :id :agg :id} {:prop :user-id :agg :id}
     * :pre-fn: 전처리
     * :prop: 부모로부터 전달 받는 키값
     * :agg: 데이터를 모으는 키값
  ## 반환
  * 객체 목록
  "
  [context arguments parent {:keys [db-key
                                    node-type
                                    superfetcher
                                    parent-id
                                    post-process-row
                                    additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [arguments (-> arguments
                      common-pre-process-arguments
                      (nullify-empty-string-arguments [:after :before]))
        {:keys [pre-fn prop agg]} parent-id
        load-id ((or pre-fn identity) (prop parent))
        {:keys [order-by
                page-direction
                page-size
                cursor-id]
         :as   page-options} (relay/build-page-options arguments)
        superfetch-arguments (merge additional-filter-opts
                                    {:id           load-id
                                     :page-options page-options
                                     :agg          agg})
        superfetch-id (hash superfetch-arguments)]
    (with-superlifter (:superlifter context)
      (-> (superlifter-api/enqueue! db-key (superfetcher superfetch-id superfetch-arguments))
          (prom/then (fn [rows]
                       (->> rows
                            (map #(relay/build-node % node-type post-process-row))
                            (relay/build-connection order-by page-direction page-size cursor-id)
                            transform-keys->camelCaseKeyword)))))))

(defn one-by
  "Lacinia 리졸버로서 config 설정에 따라 단건 조회 쿼리를 처리한다.
  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :db-key            사용할 DB 이름
    * :superfetcher      슈퍼페처
    * :post-process-row  결과 객체 목록 후처리 함수 (예: identity)
    * :parent-id: 부모로부터 전달되는 id 정보 예) {:pre-fn relay/decode-global-id->db-id :prop :id :agg :id} {:prop :user-id :agg :id}
     * :pre-fn: 전처리
     * :prop: 부모로부터 전달 받는 키값
     * :agg: 데이터를 모으는 키값
  ## 반환
  * 객체 하나
  "
  [context _arguments parent {:keys [db-key
                                     node-type
                                     superfetcher
                                     post-process-row
                                     parent-id
                                     additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [{:keys [pre-fn prop agg]} parent-id
        load-id                   ((or pre-fn identity) (prop parent))
        superfetch-arguments      (merge additional-filter-opts
                                         {:id           load-id
                                          :page-options nil
                                          :agg          agg})
        superfetch-id             (hash superfetch-arguments)]
    (with-superlifter (:superlifter context)
      (-> (superlifter-api/enqueue! db-key (superfetcher superfetch-id superfetch-arguments))
          (prom/then (fn [rows] (-> (first rows)
                                    (relay/build-node node-type post-process-row)
                                    transform-keys->camelCaseKeyword)))))))
