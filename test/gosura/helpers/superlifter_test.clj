(ns gosura.helpers.superlifter-test
  (:require [clojure.test :refer [deftest are testing run-tests]]
            [gosura.helpers.superlifter :refer [superfetch-v2]]))

(def superlifter-requests [{:id 1 :arguments {:country-code "JP" :id "1204" :page-options nil}}
                           {:id 2 :arguments {:country-code "JP" :id "1123" :page-options nil}}
                           {:id 3 :arguments {:country-code "JP" :id "370" :page-options nil}}])

(deftest superlifter-v2-test
  (testing "superlifter 요청을 받아서 table-fetcher로 올바른 batch-args를 전달해줍니다."
    (let [filter-option-args (atom {})
          args {:db-key :db
                :table-fetcher (fn [_db filter-options _]
                                 (reset! filter-option-args filter-options)
                                 [{:id "1204" :title "title1"}
                                  {:id "370" :title "title2"}
                                  {:id "1123" :title "title3"}])
                :id-column :id}
          result (superfetch-v2 superlifter-requests {} args)]
      (are [expected target] (= expected target)
        @filter-option-args {:batch-args [{:country-code "JP" :id "1204"}
                                          {:country-code "JP" :id "1123"}
                                          {:country-code "JP" :id "370"}]}
        result '([{ :id "1204" :title "title1"}] [{:id "1123" :title "title3"}] [{:id "370" :title "title2"}])))))

(comment
  (run-tests))
