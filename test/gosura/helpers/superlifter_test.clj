(ns gosura.helpers.superlifter-test
  (:require [clojure.test :refer [deftest are testing run-tests]]
            [gosura.helpers.superlifter :refer [superfetch-v2]]))

(def superlifter-requests [{:id 1 :arguments {:country-code "JP"
                                              :id "1204"
                                              :page-options {:order-by :id
                                                             :order-direction :asc
                                                             :page-direction :forward
                                                             :cursor-id nil
                                                             :cursor-ordered-value nil
                                                             :page-size 10
                                                             :limit 10}
                                              :filter-options {:test-filter "filter"}}}
                           {:id 2 :arguments {:country-code "JP" :id "1123" :page-options nil}}
                           {:id 3 :arguments {:country-code "JP" :id "370" :page-options nil}}])

(deftest superlifter-v2-test
  (testing "superlifter 요청을 받아서 db에서 값을 fetch해 올바른 순서로 반환하는 과정을 테스트합니다.
            
            ## 테스트 목록
            * `filter-option`, `page-option` 를 올바르게 만드는지
            * `fetch` 함수에 `batch-args` 내려줄 때 `filter-option`, `page-option`는 제거되는지
            * `superfetcher-v2` 함수에서 db `fetch` 후 결과 잘 반환하는지"
    (let [filter-option-args (atom {})
          page-option-args (atom {})
          args {:db-key :db
                :table-fetcher (fn [_db filter-options page-options]
                                 (reset! filter-option-args filter-options)
                                 (reset! page-option-args page-options)
                                 [{:id "1204" :title "title1"}
                                  {:id "370" :title "title2"}
                                  {:id "1123" :title "title3"}])
                :id-in-parent :id}
          result (superfetch-v2 superlifter-requests {} args)]
      (are [expected target] (= expected target)
        @filter-option-args {:test-filter "filter"
                             :batch-args [{:country-code "JP" :id "1204"}
                                          {:country-code "JP" :id "1123"}
                                          {:country-code "JP" :id "370"}]}
        @page-option-args {:order-by :id
                           :order-direction :asc
                           :page-direction :forward
                           :cursor-id nil
                           :cursor-ordered-value nil
                           :page-size 10}
        result '([{:id "1204" :title "title1"}] [{:id "1123" :title "title3"}] [{:id "370" :title "title2"}]))))
        
        (testing "superlifter에 id-in-parent 값이 :id 가 아닐 때 잘 변환합니다."
          (let [filter-option-args (atom {})
                page-option-args (atom {})
                args {:db-key :db
                      :table-fetcher (fn [_db filter-options page-options]
                                       (reset! filter-option-args filter-options)
                                       (reset! page-option-args page-options)
                                       [{:id-2 "1204" :title "title1"}
                                        {:id-2 "370" :title "title2"}
                                        {:id-2 "1123" :title "title3"}])
                      :id-in-parent :id-2}
                result (superfetch-v2 superlifter-requests {} args)]
            (are [expected target] (= expected target)
              @filter-option-args {:test-filter "filter"
                                   :batch-args [{:country-code "JP" :id-2 "1204"}
                                                {:country-code "JP" :id-2 "1123"}
                                                {:country-code "JP" :id-2 "370"}]}
              @page-option-args {:order-by :id
                                 :order-direction :asc
                                 :page-direction :forward
                                 :cursor-id nil
                                 :cursor-ordered-value nil
                                 :page-size 10}
              result '([{:id-2 "1204" :title "title1"}] [{:id-2 "1123" :title "title3"}] [{:id-2 "370" :title "title2"}])))))

(comment
  (run-tests))
