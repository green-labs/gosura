(ns gosura.helpers.db
  (:require [clojure.set]
            [com.rpl.specter :as specter]
            [honey.sql :as honeysql]
            [honey.sql.helpers :as sql-helper]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private col-with-table-name-pattern #"[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_]*")

(defn col-with-table-name?
  [col-name]
  (boolean (re-find col-with-table-name-pattern (name col-name))))

(defn col-with-table-name
  "column에 table 이름을 명시한다
   input: table-name :g, col-name :col1
   output: :g.col1"
  [table-name col-name]
  (when col-name
    (if (col-with-table-name? col-name)  ; 멱등성 부여
      col-name
      (if table-name
        (let [table-name (name table-name)
              col-name   (name col-name)]
          (keyword (str table-name "." col-name)))
        (keyword col-name)))))

(defn order-by->comparison
  [{:keys [column direction value]}]
  (let [operator (case direction
                       :ASC :>
                       :DESC :<)]
    [operator column value]))

(defn cursor-filter-pred
  "
  Example:
  order-by: [[:updated-at :DESC] [:id :ASC]]
  cursor-values: {:updated-at 1234
                  :id 100}

  output: [:or [:< :updated-at 1234]
               [:and [:= :updated-at 1234] [:> :id 100]]]"
  ([{:as   _page-options
     :keys [order-by cursor-values]}
    table-name-alias]
   (when (and order-by cursor-values)
     (let [orders (for [[column direction] order-by]
                    {:column    (col-with-table-name table-name-alias column)
                     :direction direction
                     :value     (get cursor-values column)})
           orders (reverse orders)]
       (reduce (fn [acc {:keys [column value] :as order-by}]
                 (if (empty? acc)
                   (order-by->comparison order-by)
                   [:or (order-by->comparison order-by)
                    [:and [:= column value] acc]]))
               []
               orders))))
  ([page-options]
   (cursor-filter-pred page-options nil)))

(comment
  (cursor-filter-pred {:order-by [[:price :ASC] [:updated-at :DESC] [:id :ASC]]
                       :cursor-values {:updated-at 1234
                                       :price 10000
                                       :id 100}})
  [:or [:> :price 10000]
       [:and [:= :price 10000] [:or [:< :updated-at 1234]
                                    [:and [:= :updated-at 1234] [:> :id 100]]]]])

(defn order-by-page-options
  "정렬 기준 값(order-by)과 ID를 정렬 방향(order-direction)에 맞춰 정렬하도록 하는 DSL을 만든다.
   * 인자
     * page-options
       * :order-by        정렬 기준 값 (기본값 :id)
       * :order-direction 정렬 방향 (기본값 :asc)
   * 반환 - HoneySQL DSL의 ORDER BY 절

   * 함수 작성시 주의점
     order-by 와 :id 의 정렬 방향이 일치해야 한다.
     where 절에서 (cursor.order_by, cursor.id) < (row.order_by, row.id) 으로 두 값을 묶어 자르기 때문이다.

     <잘못된 예> 두 정렬값을 서로 다른 방향으로 설정하는 경우
     ORDER BY value ASC, ID DESC
     [1] 1000, 1
     [2] 1001, 4  ; 페이지 2 커서
     [3] 1001, 3
     [4] 1001, 2
     [5] 1002, 5

     페이지 1
     WHERE (-Inf, -Inf) < (row.value, row.id)  ; => [1], [2], [3], [4], [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [1], [2]

     페이지 2
     WHERE (1001, 4) < (row.value, row.id)  ; => [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [5]  ([3], [4] 누락)

     <올바른 예>
     ORDER BY value ASC, ID DESC
     [1] 1000, 1
     [2] 1001, 2  ; 페이지 2 커서
     [3] 1001, 3
     [4] 1001, 4
     [5] 1002, 5

     페이지 1
     WHERE (-Inf, -Inf) < (row.value, row.id)  ; => [1], [2], [3], [4], [5]
     ORDER BY value ASC, id ASC LIMIT 2  ; => [1], [2]

     페이지 2
     WHERE (1001, 2) < (row.value, row.id)  ; => [3], [4], [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [3], [4]
   "
  [{:keys [order-by order-direction]}]
  [[(or order-by :id) (or order-direction :asc)]
   [:id               (or order-direction :asc)]])

(defn remove-empty-options
  "인자
  * options - WHERE 식으로 변환하기 전의 DB 조회 조건들의 해시맵

  예)
  (remove-empty-options {:a 1, :b [1 2 3], :c [], :d {}, :e nil, :f #{1 2}})
  => {:a 1, :b [1 2 3], :f #{1 2}}"
  [options]
  (let [pred #(or (nil? %)
                  (and (coll? %) (empty? %)))]
    (specter/setval [specter/MAP-VALS pred]
                    specter/NONE
                    options)))

(defn join-for-filter-options
  "지정한 규칙과 필터링 옵션에 따라 조인 조건들을 선택한다.

  인자
  * join-rules - 조인 규칙들의 시퀀스. 각 규칙은 다음 값을 갖는 해시맵이다.
    * :join - 허니 SQL 조인 식
    * :for - 조인 조건을 filter-options에서 검사할 키들의 집합
  * filter-options - WHERE 식으로 변환하기 전의 DB 조회 조건들의 해시맵

  반환 - 선택된 허니 SQL 조인 식들을 모은 벡터

  예
  (join-for-filter-options
    [{:join [:users [:= :transactions.buyer-id :users.id]]
      :for  #{:buyer-name :buyer-address :buyer-phone-number}}
     {:join [:products [:= :transactions.product-id :products.id]]
      :for  #{:product-name}}]
    {:buyer-name \"박연오\"})
  => [:users [:= :transactions.buyer-id :users.id]]
  "
  [join-rules filter-options]
  (let [filter-key-set (->> filter-options
                            remove-empty-options
                            keys
                            set)]
    (reduce (fn [join-vector join-rule]
              (if (seq (clojure.set/intersection filter-key-set (:for join-rule)))
                (into join-vector (:join join-rule))
                join-vector))
            []
            join-rules)))

(defn add-page-options
  "
  Example
  order-by: [[:updated-at :DESC] [:id :ASC]]"
  ([sql {:keys [order-by limit offset]} table-name-alias]
   (let [order-by (map (fn [[col dir]]
                         [(col-with-table-name table-name-alias col) dir])
                       order-by)]
     (cond-> sql
       order-by (assoc :order-by order-by)
       offset (assoc :offset offset)
       limit (assoc :limit limit))))
  ([sql page-options]
   (add-page-options sql page-options nil)))

(defn batch-args-filter-pred
  "WHERE (key1, key2) IN ((value1-1, value1-2), (value2-1, value2-2) ...)
   꼴의 HoneySQL 식을 반환합니다.

   ## 입력과 출력
     * 입력: key가 모두 같고 value만 다른 map의 모음을 받습니다.
            예) [{:country-code \"JP\", :id \"1204\"}
                {:country-code \"JP\", :id \"1205\"}
                {:country-code \"KR\", :id \"1206\"}]
   
     * 출력: [:in (:composite :country-code :id)
                 ((:composite \"JP\" \"1204\") (:composite \"JP\" \"1205\") (:composite \"KR\" \"1206\"))]
   
   ## HoneySQL을 통해 SQL 문으로 변환한 결과
     => [\"WHERE (country_code, id) IN ((?, ?), (?, ?), (?, ?))\" \"JP\" \"1204\" \"JP\" \"1205\" \"KR\" \"1206\"]"
  ([batch-args]
   (batch-args-filter-pred batch-args nil))
  ([batch-args table-name-alias]
   (let [column-names (->> batch-args first keys)
         column-values (map #((apply juxt column-names) %) batch-args)]
     [:in
      (cons :composite (->> column-names
                            (map #(col-with-table-name table-name-alias %))))
      (map #(cons :composite %) column-values)])))

(def query-timeout "waiting to complete query state, unit time: seconds" 10)

(def honey-sql-format-options {:dialect      :mysql
                               :quoted       true
                               :quoted-snake true})

(defn fetch!
  ([ds qs] (fetch! ds qs {}))
  ([ds qs opts]
   (let [sql (honeysql/format qs honey-sql-format-options)]
     (jdbc/execute! ds sql (merge {:timeout query-timeout} opts)))))

(defn fetch-one!
  ([ds qs] (fetch-one! ds qs {}))
  ([ds qs opts]
   (let [sql (honeysql/format (sql-helper/limit qs 1) honey-sql-format-options)]
     (jdbc/execute-one! ds sql (merge {:timeout query-timeout} opts)))))

(defn execute!
  ([ds qs] (execute! ds qs {}))
  ([ds qs opts]
   (let [sql (honeysql/format qs honey-sql-format-options)]
     (jdbc/execute! ds sql (merge {:timeout query-timeout} opts)))))

(defn execute-one!
  ([ds qs] (execute-one! ds qs {}))
  ([ds qs opts]
   (let [sql (honeysql/format qs honey-sql-format-options)]
     (jdbc/execute-one! ds sql (merge {:timeout query-timeout} opts)))))

(defn unqualified-kebab-fetch!
  [ds qs]
  (fetch! ds qs {:builder-fn rs/as-unqualified-kebab-maps}))

(defn unqualified-kebab-fetch-one!
  [ds qs]
  (fetch-one! ds qs {:builder-fn rs/as-unqualified-kebab-maps}))

(defn insert-one
  "db
   table-name: a table name; keyword; ex. :table-name
   data: {:col1 1 :col2 2 :col3 3}
   cols: a vector of keywords. ex) [:col1 :col2]"
  ([db table-name cols data]
   (insert-one db table-name cols data {}))
  ([db table-name cols data opts]
   (when (seq cols)
     (let [row   ((apply juxt cols) data)
           query {:insert-into [table-name]
                  :columns     cols
                  :values      [row]}]
       (execute-one! db query (merge {:return-keys true} opts))))))

(defn insert
  "db
   table-name: a table name; keyword; ex. :table-name
   data: [{:col1 1 :col2 2 :col3 3}, {:col1 4 :col2 5 :col3 6}, ...]
   cols: a vector of keywords. ex) [:col1 :col2]"
  ([db table-name cols data]
   (insert db table-name cols data {}))
  ([db table-name cols data opts]
   (when (seq cols)
     (let [values (map (apply juxt cols) data)
           query  {:insert-into [table-name]
                   :columns     cols
                   :values      values}]
       (execute! db query opts)))))

(defn update!
  [db table-name data where-params]
  (when (seq data)
    (let [query {:update table-name
                 :set    data
                 :where  where-params}]
      (execute! db query))))

(defn delete!
  [db table-name where-params]
  (let [query {:delete-from table-name
               :where       where-params}]
    (execute! db query)))
