(ns gosura.core-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [failjure.core :as f]
            [gosura.core :as gosura]
            [gosura.edn :refer [read-config]]
            [gosura.helpers.resolver :as gosura-resolver]))

(def normal-data (-> "test/resources/gosura/sample_resolver_configs.edn"
                     read-config))

(defn fetch [db filter-options page-options]
  [{:id 1 :content "test content" :author-id 1}])

(deftest gosura-resolver-generate-one-test
  (testing "gosura resolver의 generate-one은"
    (testing "단일 수행이 잘 성공한다"
      (let [result (gosura/generate-one normal-data)]
        (is (= result :generated))))
    (testing "단일 수행이 잘 성공하고 정상적으로 값을 반환한다"
      (gosura/generate-one {:target-ns 'gosura.test-namespace0
                            :node-type :test-type
                            :db-key    :db
                            :resolvers {:resolve-connection {:table-fetcher 'gosura.core-test/fetch}}})
      (let [resolved (eval `(gosura.test-namespace0/resolve-connection nil {} {}))]
        (is (= resolved {:count 1
                         :pageInfo {:hasPreviousPage false
                                    :hasNextPage false
                                    :startCursor "TlBZAHFpATFuAWkBMQ=="
                                    :endCursor "TlBZAHFpATFuAWkBMQ=="}
                         :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                  :node {:id "dGVzdC10eXBlOjE="
                                         :content "test content"
                                         :authorId "1"
                                         :nodeType :test-type
                                         :dbId "1"}}]}))))
    (testing "edn schema에 맞지 않는 경우는 제대로 resolvers를 생성하지 못한다"
      (let [bad-data (set/rename-keys normal-data {:target-ns :target-typo-ns})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"resolvers 생성시 스키마가 맞지 않습니다" (gosura/generate-one bad-data))))))
  #_(testing "settings in auth? 가 잘 동작한다")
  #_(testing "settings in kebab-case? 가 잘 동작한다")
  (testing "resolvers.settings in return-camel-case? false 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace1
                          :node-type :test-type
                          :db-key    :db
                          :resolvers {:resolve-connection {:settings {:return-camel-case? false}
                                                           :table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace1/resolve-connection nil {} {}))]
      (is (= resolved {:count 1
                       :page-info
                       {:has-previous-page false
                        :has-next-page false
                        :start-cursor "TlBZAHFpATFuAWkBMQ=="
                        :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  (testing "settings in return-camel-case? false 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace2
                          :node-type :test-type
                          :db-key    :db
                          :settings {:return-camel-case? false}
                          :resolvers {:resolve-connection {:table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace2/resolve-connection nil {} {}))]
      (prn :resolved resolved)
      (is (= resolved {:count 1
                       :page-info {:has-previous-page false
                                   :has-next-page false
                                   :start-cursor "TlBZAHFpATFuAWkBMQ=="
                                   :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  (testing "root in return-camel-case? false 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace3
                          :node-type :test-type
                          :db-key    :db
                          :return-camel-case? false
                          :resolvers {:resolve-connection {:table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace3/resolve-connection nil {} {}))]
      (is (= resolved {:count 1
                       :page-info {:has-previous-page false
                                   :has-next-page false
                                   :start-cursor "TlBZAHFpATFuAWkBMQ=="
                                   :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  (testing "resolvers in return-camel-case? false 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace4
                          :node-type :test-type
                          :db-key    :db
                          :resolvers {:resolve-connection {:return-camel-case? false
                                                           :table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace4/resolve-connection nil {} {}))]
      (is (= resolved {:count 1
                       :page-info {:has-previous-page false
                                   :has-next-page false
                                   :start-cursor "TlBZAHFpATFuAWkBMQ=="
                                   :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  (testing "resolvers in return-camel-case? false override 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace5
                          :node-type :test-type
                          :db-key    :db
                          :return-camel-case? true
                          :resolvers {:resolve-connection {:return-camel-case? false
                                                           :table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace5/resolve-connection nil {} {}))]
      (is (= resolved {:count 1
                       :page-info {:has-previous-page false
                                   :has-next-page false
                                   :start-cursor "TlBZAHFpATFuAWkBMQ=="
                                   :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  (testing "resolvers.settings in settings.return-camel-case? false override 가 잘 동작한다"
    (gosura/generate-one {:target-ns 'gosura.test-namespace6
                          :node-type :test-type
                          :db-key    :db
                          :settings {:return-camel-case? true}
                          :resolvers {:resolve-connection {:settings {:return-camel-case? false}
                                                           :table-fetcher 'gosura.core-test/fetch}}})
    (let [resolved (eval `(gosura.test-namespace6/resolve-connection nil {} {}))]
      (is (= resolved {:count 1
                       :page-info {:has-previous-page false
                                   :has-next-page false
                                   :start-cursor "TlBZAHFpATFuAWkBMQ=="
                                   :end-cursor "TlBZAHFpATFuAWkBMQ=="}
                       :edges [{:cursor "TlBZAHFpATFuAWkBMQ=="
                                :node {:id "dGVzdC10eXBlOjE="
                                       :content "test content"
                                       :author-id "1"
                                       :node-type :test-type
                                       :db-id "1"}}]}))))
  #_(testing "settings in required-keys-in-parent 가 잘 동작한다"))

(deftest find-resolver-fn-test
  (testing "find-resolver-fn 가"
    (testing "resolve-connection 을 잘 찾는다"
      (is (= (gosura/find-resolver-fn :resolve-connection) gosura-resolver/resolve-connection)))
    (testing "resolve-by-fk 를 잘 찾는다"
      (is (= (gosura/find-resolver-fn :resolve-by-fk) gosura-resolver/resolve-by-fk)))
    (testing "resolve-connection-by-pk-list 를 잘 찾는다"
      (is (= (gosura/find-resolver-fn :resolve-connection-by-pk-list) gosura-resolver/resolve-connection-by-pk-list)))
    (testing "resolve-connection-by-some-id 를 잘 찾는다"
      (is (= (gosura/find-resolver-fn :resolve-connection-by-sample-id) gosura-resolver/resolve-connection-by-fk)))
    (testing "찾을 수 없다면 failed? 가 true 이다"
      (is (f/failed? (gosura/find-resolver-fn :resolve-un-exist))))))

(deftest gosura-resolver-generate-all-test
  "gosura resolver의 generate-all은"
  #_(testing "여러 개의 edn 파일들로부터의 resolvers를 충돌 없이 여러개 생성한다")
  #_(testing "namespace의 충돌을 잘 방지하고 덮어쓴다 혹은 warning, error 메시지를 잘 전달한다 ; 이건 스펙에 따라 다를 듯"))

(deftest gosura-resolver-test
  "생성된 resolver들 중"
  #_(testing "resolve-connection이 정상 작동한다")
  #_(testing "resolve-by-fk가 정상 작동한다")
  #_(testing "resolve-by-example-id가 정상 작동한다")
  #_(testing "resolve-node가 정상 작동한다"))

(comment
  (clojure.test/run-test gosura-resolver-generate-one-test))
