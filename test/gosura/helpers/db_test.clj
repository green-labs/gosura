(ns gosura.helpers.db-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [gosura.helpers.db :as db]))

(deftest utility-test
  (testing "remove-empty-options"
    (let [options {:a 1, :b [1 2 3], :c [], :d {}, :e nil, :f #{1 2}}]
      (is (= {:a 1, :b [1 2 3], :f #{1 2}}
             (db/remove-empty-options options))))))

(defn submap? [a b]
  (= a (select-keys b (keys a))))

(deftest add-page-options
  (testing "order by :id"
    (let []
      ;; explicit
      (is (submap? (db/add-page-options {} {:order-direction :desc
                                            :order-by        :id})
                   {:order-by [[:id :desc]]}))
      ;; implicit
      (is (submap? (db/add-page-options {} {:order-direction :desc})
                   {:order-by [[:id :desc]]})))))

(deftest batch-args-filter-pred-test
  (testing "batch-args를 HoneySQL에서 사용할 수 있는 데이터로 반환합니다."
    (let [result (db/batch-args-filter-pred
                  [{:country-code "JP", :id "1204"}
                   {:country-code "JP", :id "1205"}
                   {:country-code "KR", :id "1206"}])]
      (is (= [:in [:composite :country-code :id]
              [[:composite "JP" "1204"]
               [:composite "JP" "1205"]
               [:composite "KR" "1206"]]]
             result))))
  
  (testing "table-alias가 지정되어 있을 때, table-alias가 적용되어
            batch-args를 HoneySQL에서 사용할 수 있는 데이터로 반환합니다."
    (let [result (db/batch-args-filter-pred
                  [{:country-code "JP", :id "1204"}
                   {:country-code "JP", :id "1205"}
                   {:country-code "KR", :id "1206"}]
                  :s)]
      (is (= [:in [:composite :s.country-code :s.id]
              [[:composite "JP" "1204"]
               [:composite "JP" "1205"]
               [:composite "KR" "1206"]]]
             result)))))

(comment
  (run-tests))
