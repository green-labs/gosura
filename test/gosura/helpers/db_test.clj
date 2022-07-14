(ns gosura.helpers.db-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [gosura.helpers.db :as db]))

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
