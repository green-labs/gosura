(ns gosura.helpers.superlifter
  (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [superlifter.api :as api]))

;; Superlifter.lacinia가 Pedestal 의존성이 있기 때문에, ns를 불러올 수 없음.
;; https://github.com/oliyh/superlifter/blob/master/src/superlifter/lacinia.clj
;; 위 코드를 참고하여 동일한 역할을 하는 함수와 매크로를 구현

(defn ->lacinia-promise [sl-result]
  (let [l-prom (resolve/resolve-promise)]
    (api/unwrap (fn [result] (resolve/deliver! l-prom result))
                (fn [error] (resolve/deliver! l-prom nil {:message (.getMessage error)}))
                sl-result)
    l-prom))

(defmacro with-superlifter [ctx body]
  `(api/with-superlifter ~ctx
     (->lacinia-promise ~body)))

(defn superfetch
  "
  ## 인자
  * id-column   superfetch 결과의 매핑 단위가 되는 (parent 하나에 대응되는) 컬럼 이름
  * filter-key  table-fetcher에서 in 조건을 걸어줄 컬럼 이름"
  [many
   env
   {:keys [db-key table-fetcher id-column filter-key]}]
  (let [db (get env db-key)
        arguments-list (map :arguments many)
        ids (->> arguments-list
                 (map :id)
                 (map str))
        etc-keys (->> (keys (first arguments-list))
                      (remove #{:id :filter-options :page-options}))
        etc-filter-options (reduce (fn [acc val]
                                     (merge acc {val (->> arguments-list
                                                          (map val)
                                                          (map str))}))
                                   {}
                                   etc-keys) ; 인증 관련되어서 추가 필터를 적용하기 위함
        filter-options (merge (->> arguments-list first :filter-options) ; base filter options
                              {filter-key ids}
                              {:batch-args arguments-list} ; 추가 필터값이 포함된 전체 argument list
                              etc-filter-options)
        page-options (-> (->> arguments-list first :page-options) ; base page options
                         (dissoc :limit))  ; (연오) foolproof: 페치할 때 LIMIT 하면 안 된다. 페치 -> ID별 그룹 -> 그룹별 LIMIT
        id->rows (->> (table-fetcher db filter-options page-options)
                      (map #(update % id-column str))  ; ids가 str로 입력되므로 맞춤
                      (group-by id-column))]
    (map id->rows ids))) ; rows를 ids 순서대로 배치

(defmacro superfetcher
  "superfetcher 기본 form을 쉽게 제공한다
   defrecord를 생성하므로 name은 PascalCase로 작성하는 걸 원칙으로 한다
   호출할 때에는 ->name 으로 사용한다
   생성예시) (superfetcher FetchByExampleId {...})
   사용예시) ->FetchByExampleId
   "
  [name params]
  (let [id (symbol "id")
        arguments (symbol "arguments")]
    `(api/def-superfetcher ~name [~id ~arguments]
       (fn [many# env#]
         (superfetch many# env# ~params)))))
