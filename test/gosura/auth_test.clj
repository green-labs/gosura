(ns gosura.auth-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [failjure.core :as f]
            [gosura.auth :as gosura-auth]))

(deftest extract-auth-map-test
  (testing "auth의 시그니처가 fn 일 때, 인증에 대한 mapping 결과 컬럼을 잘 반환한다"
    (let [auth            identity
          result          (gosura-auth/extract-auth-map auth)
          expected-result nil]
      (is (= result expected-result))))
  (testing "auth의 시그니처가 (fn keyword) 일 때, 인증에 대한 mapping 결과 컬럼을 잘 반환한다"
    (let [auth            [identity :user-id]
          result          (gosura-auth/extract-auth-map auth)
          expected-result :user-id]
      (is (= result expected-result))))
  (testing "auth의 시그니처가 (fn map) 일 때, 인증에 대한 mapping 결과 컬럼을 잘 반환한다"
    (let [auth            [identity {:admin :user-id}]
          result          (gosura-auth/extract-auth-map auth)
          expected-result {:admin :user-id}]
      (is (= result expected-result)))))

(deftest auth-filter-opts-test
  (let [auth0 (fn [ctx]
                (boolean (get-in ctx [:identity :id])))
        auth1 (fn [ctx role]
                (let [user-role (keyword (get-in ctx [:identity :role]))]
                  (if (set? user-role)
                    (role user-role)
                    (when (role user-role) ; map
                      user-role))))
        unauthorized (f/fail "Unauthorized")]
    (testing "auth가 없을 때, 아무일도 일어나지 않는다"
      (let [auth            nil
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/auth-filter-opts auth ctx)
            expected-result nil]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 fn 일 때, auth0 인증을 통과한 경우 추가 필터는 없다"
      (let [auth            auth0
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/auth-filter-opts auth ctx)
            expected-result nil]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 fn 일 때, auth0 인증에 실패하면 Unauthorized 메시지를 반환한다."
      (let [auth            auth0
            ctx             {}
            result          (gosura-auth/auth-filter-opts auth ctx)]
        (is (= result unauthorized))))
    (testing "auth의 시그니처가 (fn keyword) 일 때, auth0 인증을 통과한 후 추가 필터는 {keyword : auth-id}를 잘 반환한다"
      (let [auth            [auth0 :user-id]
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/auth-filter-opts auth ctx)
            expected-result {:user-id "1"}]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 (fn keyword) 일 때, auth0 인증에 실패하면 Unauthorized 메시지를 반환한다."
      (let [auth            [auth0 :user-id]
            ctx             {}
            result          (gosura-auth/auth-filter-opts auth ctx)]
        (is (= result unauthorized))))
    (testing "auth의 시그니처가 (fn set) 일 때, auth1 인증을 통과한 후 추가 필터는 없다"
      (let [auth            [auth1 #{:admin}]
            ctx             {:identity {:id   "1"
                                        :role "admin"}}
            result          (gosura-auth/auth-filter-opts auth ctx)
            expected-result nil]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 (fn set) 일 때, auth1 인증에 실패하면 Unauthorized 메세지를 반환한다"
      (let [auth            [auth1 #{:admin}]
            ctx             {:identity {:id   "1"
                                        :role "buyer"}}
            result          (gosura-auth/auth-filter-opts auth ctx)]
        (is (= result unauthorized))))
    (testing "auth의 시그니처가 (fn map) 일 때, auth1 인증을 통과한 후 추가 필터는 {auth-map에 해당하는 value : auth-id}를 잘 반환한다"
      (let [auth            [auth1 {:admin :user-id}]
            ctx             {:identity {:id   "1"
                                        :role "admin"}}
            result          (gosura-auth/auth-filter-opts auth ctx)
            expected-result {:user-id "1"}]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 (fn map) 일 때, auth1 Unauthorized 메세지를 반환한다"
      (let [auth            [auth1 {:admin :user-id}]
            ctx             {:identity {:id   "1"
                                        :role "buyer"}}
            result          (gosura-auth/auth-filter-opts auth ctx)]
        (is (= result unauthorized))))))

(comment
  (run-tests))
