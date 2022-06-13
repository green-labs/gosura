(ns gosura.core-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [greenlabs.gosura.core :as gosura]
            [greenlabs.gosura.helpers.resolver :as gosura-resolver]))

(def normal-data (-> "test/resources/greenlabs/gosura/sample_resolver_configs.edn"
                     slurp
                     read-string))

(deftest gosura-resolver-generate-one-test
  (testing "gosura resolver의 generate-one은"
    (testing "단일 수행이 잘 성공한다"
      (let [result (gosura/generate-one normal-data)]
        (is (= result :generated))))
    (testing "edn schema에 맞지 않는 경우는 제대로 resolvers를 생성하지 못한다"
      (let [bad-data (set/rename-keys normal-data {:target-ns :target-typo-ns})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"resolvers 생성시 스키마가 맞지 않습니다" (gosura/generate-one bad-data))))))
  #_(testing "settings in auth? 가 잘 동작한다")
  #_(testing "settings in kebab-case? 가 잘 동작한다")
  #_(testing "settings in return-camel-case? 가 잘 동작한다")
  #_(testing "settings in required-keys-in-parent 가 잘 동작한다"))

(deftest match-resolve-fn-test
  (testing "gosura resolver에 필요한 match-resolve-fn은"
    (testing "resolve-connection을 잘 매칭한다"
      (is (= (gosura/match-resolve-fn :resolve-connection) gosura-resolver/resolve-connection)))
    (testing "resolve-by-fk를 잘 매칭한다"
      (is (= (gosura/match-resolve-fn :resolve-by-fk) gosura-resolver/resolve-by-fk)))
    (testing "resolve-connection-by-pk-list를 잘 매칭한다"
      (is (= (gosura/match-resolve-fn :resolve-connection-by-pk-list) gosura-resolver/resolve-connection-by-pk-list)))
    (testing "resolve-connection-by-some-id를 잘 매칭한다"
      (is (= (gosura/match-resolve-fn :resolve-connection-by-sample-id) gosura-resolver/resolve-connection-by-fk)))
    (testing "매칭할게 없다면 에러 메시지를 잘 뱉어낸다"
      (let [bad-resolver :bad-resolver]
        (is (= (format "Resolver matching failed because of No matching clause: %s" (name bad-resolver))
               (:message (gosura/match-resolve-fn bad-resolver))))))))

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
