(ns gosura.helpers.superlifter-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [gosura.helpers.superlifter :refer [superfetch-v2]]))

(deftest superlifter-v2-test
  (testing ""
    (let [filter-options-called-args (atom {})
          many '({:id -1501533529, :arguments {:country-code "JP", :id "1204", :page-options nil}}
                 {:id 1497647185, :arguments {:country-code "JP", :id "1123", :page-options nil}}
                 {:id 1482270568, :arguments {:country-code "JP", :id "370", :page-options nil}}
                 {:id 1016130167, :arguments {:country-code "JP", :id "1594", :page-options nil}}
                 {:id 803703884, :arguments {:country-code "JP", :id "1025", :page-options nil}}
                 {:id 926836910, :arguments {:country-code "JP", :id "596", :page-options nil}}
                 {:id 167611840, :arguments {:country-code "JP", :id "1970", :page-options nil}})
          args {:db-key :db
                :table-fetcher (fn [_db filter-options _]
                                 (reset! filter-options-called-args filter-options)
                                 [{:country-code "JP", :id "1204"}
                                  {:country-code "JP", :id "1123"}
                                  {:country-code "JP", :id "370"}
                                  {:country-code "JP", :id "1594"}
                                  {:country-code "JP", :id "1025"}
                                  {:country-code "JP", :id "596"}
                                  {:country-code "JP", :id "1970"}])
                :id-column :id}]
      (superfetch-v2 many {} args)
      (is (= @filter-options-called-args {:batch-args
                                          [{:country-code "JP", :id "1204"}
                                           {:country-code "JP", :id "1123"}
                                           {:country-code "JP", :id "370"}
                                           {:country-code "JP", :id "1594"}
                                           {:country-code "JP", :id "1025"}
                                           {:country-code "JP", :id "596"}
                                           {:country-code "JP", :id "1970"}]})))))

(comment
  (run-tests))
