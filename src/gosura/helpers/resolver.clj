(ns gosura.helpers.resolver
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :refer [ends-with?]]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [gosura.auth :as auth]
            [gosura.helpers.error :as error]
            [gosura.helpers.relay :as relay]
            [gosura.helpers.response :as response]
            [gosura.helpers.superlifter :refer [with-superlifter]]
            [gosura.util :as util :refer [transform-keys->kebab-case-keyword
                                          transform-keys->camelCaseKeyword
                                          update-resolver-result]]
            [medley.core :as medley]
            [promesa.core :as prom]
            [superlifter.api :as superlifter-api]))

(defmacro keys-not-found
  [parent required-keys-in-parent]
  `(->> ~required-keys-in-parent
        (keep #(when-not (% ~parent) %))
        (into [])))

(defmacro wrap-resolver-body
  "GraphQL 리졸버가 공통으로 해야 할 auth 처리, case 변환 처리를 resolver body의 앞뒤에서 해 주도록 wrapping합니다.

  this, ctx, arg, parent: 상위 리졸버 생성 매크로에서 만든 심벌
  option: 리졸버 선언에 지정된 옵션 맵
  args: 리졸버 선언의 argument vector 부분
  body: 리졸버의 body expression 부분"
  [{:keys [this ctx arg parent]} option args body]
  (let [{:keys [auth kebab-case? return-camel-case? required-keys-in-parent]
         :or   {kebab-case?             true
                return-camel-case?      true
                required-keys-in-parent []}} option

        result (gensym 'result_)
        authorized? `(auth/apply-fn-with-ctx-at-first ~auth ~ctx)
        arg' (if kebab-case? `(transform-keys->kebab-case-keyword ~arg) arg)
        parent' (if kebab-case? `(transform-keys->kebab-case-keyword ~parent) parent)
        keys-not-found `(keys-not-found ~parent' ~required-keys-in-parent)
        params (if (nil? this) [ctx arg' parent'] [this ctx arg' parent'])
        let-mapping (vec (interleave args params))]

    `(if (seq ~keys-not-found)
       (error/error {:message (format "%s keys are needed in parent" ~keys-not-found)})
       (if ~authorized?
         (let [~result (do (let ~let-mapping ~@body))]
           (cond-> ~result
                   ~return-camel-case? (update-resolver-result transform-keys->camelCaseKeyword)))
         (resolve-as nil {:message "Unauthorized"})))))

(defn parse-fdecl
  "함수의 이름 뒤에 오는 선언부를 파싱합니다. doc-string와 option이 있을 수도 있고, 없을 수도 있기 때문에 그를 적절히 파싱해 줍니다.
  (fdecl이라는 이름은 core의 defn 구현에서 쓰이는 이름을 따왔습니다)
  "
  [fdecl]
  (let [[doc-string args] (if (string? (first fdecl))
                            [(first fdecl) (rest fdecl)]
                            [nil fdecl])
        [option [args & body]] (if (map? (first args))
                                 [(first args) (rest args)]
                                 [{} args])]
    {:doc-string doc-string
     :option option
     :args args
     :body body}))

(defmacro defresolver
  "GraphQL resolver 함수를 만듭니다.

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
  :node-type - relay resolver 일때 설정하면, edge/node와 :pageInfo의 start/endCursor 처리를 같이 해줍니다.
  :return-camel-case? - 반환값을 camelCase 로 변환할지 설정합니다. (기본값 false)
  :required-keys-in-parent - 부모(hash-map)로부터 필요한 required keys를 설정합니다."
  {:arglists '([name doc-string? option? args & body])}
  [name & fdecl]
  (let [{:keys [doc-string option args body]} (parse-fdecl fdecl)
        header (if doc-string [name doc-string] [name])]
    `(defn ~@header [ctx# arg# parent#]
       (wrap-resolver-body {:ctx ctx#
                            :arg arg#
                            :parent parent#} ~option ~args ~body))))

(defmacro defnode
  {:arglists '([node-type doc-string? option? args & body])}
  [node-type & fdecl]
  (let [{:keys [option args body]} (parse-fdecl fdecl)
        node-type (keyword node-type)
        node-type-pascal (csk/->PascalCaseKeyword node-type)]
    `(defmethod relay/node-resolver ~node-type [this# ctx# arg# parent#]
       (let [result# (wrap-resolver-body {:this this#
                                          :ctx ctx#
                                          :arg arg#
                                          :parent parent#} ~option ~args ~body)]
         (-> result#
             (relay/build-node ~node-type)
             transform-keys->camelCaseKeyword
             (tag-with-type ~node-type-pascal))))))

;;; Utility functions

(defn decode-global-ids-in-arguments
  "인자 맵의 ID들(열이 ids, 그리고 -ids로 끝나는 것들)의 값을 글로벌 ID에서 ID로 디코드한다."
  [arguments]
  (-> arguments
      (medley/update-existing :ids #(map relay/decode-global-id->db-id %))
      (util/update-vals-having-keys (->> (keys arguments)
                                         (filter #(= [\- \i \d \s]
                                                     (take-last 4 (name %)))))
                                    #(map relay/decode-global-id->db-id %))))

(defn decode-global-id-in-arguments
  "인자 맵의 ID들(열이 id, 그리고 -id로 끝나는 것들)의 값을 글로벌 ID에서 ID로 디코드한다."
  [arguments]
  (-> arguments
      (medley/update-existing :id relay/decode-global-id->db-id)
      (util/update-vals-having-keys (->> (keys arguments)
                                         (filter #(ends-with? (name %) "-id")))
                                    relay/decode-global-id->db-id)))

(defn common-pre-process-arguments
  "인자 맵에 일반적인 전처리를 한다."
  [arguments]
  (->> arguments
       transform-keys->kebab-case-keyword
       decode-global-ids-in-arguments))

(defn- nullify-empty-string-arguments
  [arguments ks]
  (reduce (fn [arguments k]
            (if (= (get arguments k) "")
              (dissoc arguments k)
              arguments))
          arguments
          ks))

;;; *** Public Interface ***

(defn resolve-by-parent-pk
  "parent 객체의 primary key (보통 id)와 child의 foreign key 기반으로 child 하나를 resolve한다.
  parent:child가 1:0..1일 것을 가정함. DB 제약으로 child가 n개 붙는 것을 막을 수는 없지만,
  GraphQL에서 Parent->Optional<Child> 관계를 지원하기 위한 용도임
  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력 (지원 안함)
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :db-key           사용할 DB 이름
    * :superfetcher     슈퍼페처
    * :post-process-row 결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  "
  [context _arguments parent {:keys [db-key node-type superfetcher
                                     post-process-row
                                     additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [parent-id (-> parent :id relay/decode-global-id->db-id)
        superfetch-arguments (merge additional-filter-opts
                                    {:id           parent-id
                                     :page-options nil})
        superfetch-id (hash superfetch-arguments)]
    (with-superlifter (:superlifter context)
                      (-> (superlifter-api/enqueue! db-key (superfetcher superfetch-id superfetch-arguments))
                          (prom/then (fn [rows]
                                       (-> (first rows)
                                           (relay/build-node node-type post-process-row)
                                           transform-keys->camelCaseKeyword)))))))

(defn resolve-by-fk
  "Lacinia 리졸버로서 config 설정에 따라 단건 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :db-key            사용할 DB 이름
    * :superfetcher      슈퍼페처
    * :post-process-row  결과 객체 목록 후처리 함수 (예: identity)
    * :fk-in-parent      부모 엔티티로부터 이 엔티티의 ID를 가리키는 FK 칼럼 이름 (예: :gl-crop-id)

  ## 반환
  * 객체 하나
  "
  [context _arguments parent {:keys [db-key node-type superfetcher
                                     post-process-row fk-in-parent
                                     additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [fk (get parent fk-in-parent)
        ; TODO: 생각해볼 점: 여기서 fk가 nil이면 아래의 로직을 안 타고 바로 nil을 반환해도 될 것 같음
        page-options nil  ; don't limit page size to 1 because superfetcher fetches many rows
        superfetch-arguments (merge additional-filter-opts
                                    {:id           fk
                                     :page-options page-options})
        superfetch-id (hash superfetch-arguments)]
    (with-superlifter (:superlifter context)
                      (-> (superlifter-api/enqueue! db-key (superfetcher superfetch-id superfetch-arguments))
                          (prom/then (fn [rows] (-> (first rows)
                                                    (relay/build-node node-type post-process-row)
                                                    transform-keys->camelCaseKeyword)))))))

(defn resolve-connection
  "Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :node-type
    * :db-key                조회할 데이터베이스 이름 (예: :db, :farmmorning-db)
    * :table-fetcher         테이블 조회 함수 (예: bulk-sale/fetch-crops)
    * :pre-process-arguments
    * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)
    * :additional-filter-options 추가로 필요한 필터 옵션을 제공 받는다 예) jwt 토큰이 담고 있는 id가 가리키는 실제적 이름 등 (예: user-id(대부분), buyer-id)

  ## 반환
  * Connection
  "
  [context arguments _parent {:keys [node-type
                                     db-key
                                     table-fetcher
                                     pre-process-arguments
                                     post-process-row
                                     additional-filter-opts]}]
  (let [db (get context db-key)
        arguments (-> arguments
                      common-pre-process-arguments
                      (nullify-empty-string-arguments [:after :before])
                      pre-process-arguments)
        {:keys [order-by
                page-direction
                page-size
                cursor-id] :as page-options} (relay/build-page-options arguments)
        filter-options (relay/build-filter-options arguments additional-filter-opts)
        rows (table-fetcher db filter-options page-options)]
    (->> rows
         (map #(relay/build-node % node-type post-process-row))
         (relay/build-connection order-by page-direction page-size cursor-id)
         transform-keys->camelCaseKeyword)))

;; FIXME: N+1 쿼리임
(defn resolve-connection-by-pk-list
  "Primary Key를 기준으로 데이터를 조회하며 Connection Spec에 맡게 반환한다
   Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
   * :pk-list-name pk-list로 데이터를 조회할 때 이름
   * :pk-list-name-in-parent pk-list로 데이터를 조회할 때 부모로부터 전달 받는 이름
   * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  "
  [context arguments parent {:keys [db-key node-type table-fetcher pk-list-name
                                    post-process-row pk-list-name-in-parent
                                    additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [db (get context db-key)
        arguments (-> arguments
                      common-pre-process-arguments
                      (nullify-empty-string-arguments [:after :before]))
        {:keys [order-by
                page-direction
                page-size
                cursor-id]
         :as   page-options} (relay/build-page-options arguments)
        pk-list (get parent pk-list-name-in-parent)
        decoded-pk-list (map relay/decode-global-id->db-id pk-list)
        filter-options (relay/build-filter-options (assoc arguments (or pk-list-name :ids) decoded-pk-list) additional-filter-opts)
        rows (table-fetcher db filter-options page-options)]
    (->> rows
         (map #(relay/build-node % node-type post-process-row))
         (relay/build-connection order-by page-direction page-size cursor-id)
         transform-keys->camelCaseKeyword)))

(defn resolve-connection-by-fk
  "Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :superfetcher          슈퍼페처
    * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  "
  [context arguments parent {:keys [db-key node-type superfetcher
                                    post-process-row
                                    additional-filter-opts]}]
  {:pre [(some? db-key)]}
  (let [arguments (-> arguments
                      common-pre-process-arguments
                      (nullify-empty-string-arguments [:after :before]))
        parent-id (-> parent :id relay/decode-global-id->db-id)
        {:keys [order-by
                page-direction
                page-size
                cursor-id]
         :as   page-options} (relay/build-page-options arguments)
        superfetch-arguments (merge additional-filter-opts
                                    {:id           parent-id
                                     :page-options page-options})
        superfetch-id (hash superfetch-arguments)]
    (with-superlifter (:superlifter context)
                      (-> (superlifter-api/enqueue! db-key (superfetcher superfetch-id superfetch-arguments))
                          (prom/then (fn [rows]
                                       (->> rows
                                            (map #(relay/build-node % node-type post-process-row))
                                            (relay/build-connection order-by page-direction page-size cursor-id)
                                            transform-keys->camelCaseKeyword)))))))

; TODO 다른 mutation helper 함수와 통합
(defn pack-mutation-result
  "Lacinia 변환 리졸버 응답용 변환 내역을 꾸며 반환한다."
  [db db-fetcher filter-options {:keys [node-type post-process-row]}]
  {:result (-> (first (db-fetcher db filter-options nil))
               (relay/build-node node-type post-process-row)
               transform-keys->camelCaseKeyword)})

(defn resolve-create-one
  [ctx args _parent {:keys [node-type
                            db-key
                            table-fetcher
                            mutation-fn
                            mutation-tag
                            additional-filter-opts
                            pre-process-arguments
                            post-process-row]}]
  (let [db (db-key ctx)
        {:keys [input]} args
        input (merge (-> input
                         util/keyword-vals->string-vals
                         decode-global-id-in-arguments
                         decode-global-ids-in-arguments
                         pre-process-arguments)
                     additional-filter-opts)]
    (try
      (if-let [id (->> input
                       (mutation-fn db)
                       :generated-key)] ; 한계) auto-gen key가 있는 경우에만 사용 가능
        (response/->mutation-response (-> (table-fetcher db {:id id} {})
                                          first
                                          (relay/build-node node-type post-process-row))
                                      mutation-tag)
        response/not-exist-error)
      (catch Exception e
        (response/server-error (get-in ctx [:config :profile]) e)))))

(defn resolve-update-one
  [ctx args _parent {:keys [node-type
                            db-key
                            table-fetcher
                            mutation-fn
                            mutation-tag
                            additional-filter-opts
                            pre-process-arguments
                            post-process-row]}]
  (let [db (db-key ctx)
        {:keys [input id]} args
        decoded-id (relay/decode-global-id->db-id id)
        input (merge (-> input
                         util/keyword-vals->string-vals
                         decode-global-id-in-arguments
                         decode-global-ids-in-arguments
                         pre-process-arguments)
                     additional-filter-opts)]
    (try
      (when-not (mutation-fn db input decoded-id)
        (throw (ex-info "DB update의 대상이 잘못되었습니다" {:target-id decoded-id})))
      (response/->mutation-response (-> (table-fetcher db {:id decoded-id} {})
                                        first
                                        (relay/build-node node-type post-process-row))
                                    mutation-tag)
      (catch Exception e
        (response/server-error (get-in ctx [:config :profile]) e)))))

(defn resolve-delete-one
  [ctx args _parent {:keys [db-key
                            mutation-fn
                            additional-filter-opts]}]
  (let [db           (db-key ctx)
        {:keys [id]} args
        decoded-id   (relay/decode-global-id->db-id id)]
    (try
      (when-not (mutation-fn db additional-filter-opts decoded-id)
        (throw (ex-info "DB delete의 대상이 잘못되었습니다" {:target-id decoded-id})))
      (response/->delete-response id)
      (catch Exception e
        (response/server-error (get-in ctx [:config :profile]) e)))))

(defn resolve-update-multi
  [ctx args _parent {:keys [node-type
                            db-key
                            table-fetcher
                            mutation-fn
                            mutation-tag
                            additional-filter-opts
                            pre-process-arguments
                            post-process-row]}]
  (let [db              (db-key ctx)
        {:keys [input]} args
        input           (merge (-> input
                                   util/keyword-vals->string-vals
                                   decode-global-id-in-arguments
                                   decode-global-ids-in-arguments
                                   pre-process-arguments)
                               additional-filter-opts)]
    (try
      (let [affected-ids (mutation-fn db input)]
        (response/->mutation-response (->> (table-fetcher db {:ids affected-ids} {})
                                           (map #(relay/build-node % node-type post-process-row)))
                                      mutation-tag))
      (catch Exception e
        (response/server-error (get-in ctx [:config :profile]) e)))))

(defn resolve-one
  [context arguments _parent {:keys [node-type
                                     db-key
                                     fetch-one
                                     pre-process-arguments
                                     post-process-row
                                     additional-filter-opts]}]
  (let [db             (get context db-key)
        arguments      (-> arguments
                           common-pre-process-arguments
                           pre-process-arguments)
        filter-options (relay/build-filter-options arguments additional-filter-opts)
        row            (fetch-one db filter-options {})]
    (-> (relay/build-node row node-type post-process-row)
        transform-keys->camelCaseKeyword
        (tag-with-type (csk/->PascalCaseKeyword node-type)))))
