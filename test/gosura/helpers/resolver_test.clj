(ns gosura.helpers.resolver-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [gosura.helpers.resolver :as gosura-resolver]
            [gosura.helpers.resolver2 :as gosura-resolver2]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; defresolver2 test

(defn user-auth
  ([ctx]
   (boolean (:identity ctx)))
  ([ctx auth-column-name]
   (when-let [id (get-in ctx [:identity :id])]
     {auth-column-name id})))

(defn get-country-code
  "cc is country-code"
  [ctx]
  (get-in ctx [:identity :cc]))

(def auth-column-name :userId)
(def arg-in-resolver (atom {}))

(gosura-resolver2/defresolver test-resolver
  {:auth [user-auth auth-column-name]}
  [ctx arg parent]
  (reset! arg-in-resolver arg)
  {:ctx    ctx
   :arg    arg
   :parent parent})

(deftest defresolver2-test
  (testing "auth 설정이 잘 동작한다"
    (let [ctx    {:identity {:id "1"}}
          arg    {:intArg 1
                  :strArg "str"}
          parent {}
          result (test-resolver ctx arg parent)]
      (is (= (-> result
                 :arg
                 :userId) "1"))))
  (testing "arg/parent가 default True로 kebab-case 설정이 잘 동작한다"
    (let [ctx    {:identity {:id "1"}}
          arg    {:intArg 1
                  :strArg "str"}
          parent {}
          _      (test-resolver ctx arg parent)]
      (is (= @arg-in-resolver {:int-arg 1
                               :str-arg "str"
                               :user-id "1"}))))
  (testing "return value return-camel-case? 설정이 잘 동작한다"
    (let [ctx    {:identity {:id "1"}}
          arg    {:intArg 1
                  :strArg "str"}
          parent {}
          result (test-resolver ctx arg parent)]
      (is (= result {:ctx    {:identity {:id "1"}}
                     :arg    {:intArg 1
                              :strArg "str"
                              :userId "1"}
                     :parent {}}))))
  (testing "required-keys-in-parent 설정이 잘 동작한다"
    (let [_            (gosura-resolver2/defresolver test-resolver-2
                         {:auth                    [user-auth auth-column-name]
                          :required-keys-in-parent [:my-col]}
                         [ctx arg parent]
                         {:ctx    ctx
                          :arg    arg
                          :parent parent})
          ctx          {:identity {:id "1"}}
          arg          {:intArg 1
                        :strArg "str"}
          parent       {:myCol 1}
          empty-parent {}
          ok-result    (test-resolver-2 ctx arg parent)
          fail-result  (test-resolver-2 ctx arg empty-parent)]
      (is (= ok-result {:ctx    {:identity {:id "1"}}
                        :arg    {:intArg 1
                                 :strArg "str"
                                 :userId "1"}
                        :parent {:myCol 1}}))
      (is (= (-> fail-result
                 :resolved-value
                 :data
                 :message) "[:my-col] keys are needed in parent"))))
  (testing "filter 로직이 잘 동작한다"
    (let [_      (gosura-resolver2/defresolver test-resolver-3
                   {:auth    [user-auth auth-column-name]
                    :filters {:country-code get-country-code}}
                   [ctx arg parent]
                   (reset! arg-in-resolver arg)
                   {:ctx    ctx
                    :arg    arg
                    :parent parent})
          ctx    {:identity {:id "1"
                             :cc "KR"}}
          arg    {:intArg 1
                  :strArg "str"}
          parent {}
          result (test-resolver-3 ctx arg parent)]
      (is (= result {:ctx    {:identity {:id "1"
                                         :cc "KR"}}
                     :arg    {:intArg      1
                              :strArg      "str"
                              :userId      "1"
                              :countryCode "KR"}
                     :parent {}}))))
  (testing "auth 설정이 false를 반환해도 잘 동작한다"
    (let [_      (gosura-resolver2/defresolver test-resolver-4
                   {:auth user-auth}
                   [ctx arg parent]
                   {:ctx    ctx
                    :arg    arg
                    :parent parent})
          ctx    {}
          arg    {}
          parent {}
          result (test-resolver-4 ctx arg parent)]
      (is (= (-> result
                 :resolved-value
                 :data
                 :message) "Unauthorized")))))

(comment
  (run-tests))
