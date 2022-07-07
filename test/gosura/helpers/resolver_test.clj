(ns gosura.helpers.resolver-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [gosura.helpers.resolver :as gosura-resolver]))

(deftest decode-global-id-in-arguments-test
  (testing "argments에 -id로 끝나는 것들이 nil이면 디코딩하지 않고 그대로 둔다"
    (let [args     {:test-id nil}
          result   (gosura-resolver/decode-global-id-in-arguments args)
          expected {:test-id nil}]
      (is (= result expected))))
  (testing "argments에 -id로 끝나는 것들이 base64 인코딩 되어있으면 디코딩한 결과를 가진다"
    (let [args     {:test-id "dGVzdDox"}
          result   (gosura-resolver/decode-global-id-in-arguments args)
          expected {:test-id "1"}]
      (is (= result expected))))
  (testing "argments에 -id로 끝나는 것들 외에는 영향을 받지 않는다"
    (let [args     {:test-id   "dGVzdDox"
                    :test-name "test-name"}
          result   (gosura-resolver/decode-global-id-in-arguments args)
          expected {:test-id   "1"
                    :test-name "test-name"}]
      (is (= result expected))))
  (testing "argments에 id도 잘 디코딩된다"
    (let [args     {:id "dGVzdDox"}
          result   (gosura-resolver/decode-global-id-in-arguments args)
          expected {:id "1"}]
      (is (= result expected)))))


(deftest decode-global-ids-in-arguments-test
  (testing "argments에 -ids로 끝나는 것들이 nil이면 디코딩하지 않고 그대로 둔다"
    (let [args     {:test-ids nil}
          result   (gosura-resolver/decode-global-ids-in-arguments args)
          expected {:test-ids nil}]
      (is (= result expected))))
  (testing "argments에 -ids로 끝나는 것들이 base64 인코딩 되어있으면 디코딩한 결과를 가진다"
    (let [args     {:test-ids ["dGVzdDox" "dGVzdDoy"]}
          result   (gosura-resolver/decode-global-ids-in-arguments args)
          expected {:test-ids ["1" "2"]}]
      (is (= result expected))))
  (testing "argments에 -ids로 끝나는 것들 외에는 영향을 받지 않는다"
    (let [args     {:test-ids  ["dGVzdDox" "dGVzdDoy"]
                    :test-name "test-name"}
          result   (gosura-resolver/decode-global-ids-in-arguments args)
          expected {:test-ids  ["1" "2"]
                    :test-name "test-name"}]
      (is (= result expected))))
  (testing "argments에 ids도 잘 디코딩된다"
    (let [args     {:ids ["dGVzdDox" "dGVzdDoy"]}
          result   (gosura-resolver/decode-global-ids-in-arguments args)
          expected {:ids ["1" "2"]}]
      (is (= result expected)))))

(deftest resolve-connection-test)

(deftest resolve-by-fk-test)

(deftest resolve-by-parent-pk-test)

(deftest resolve-connection-by-fk)

(deftest resolve-connection-by-pk-list-test)

(comment
  (run-tests))
