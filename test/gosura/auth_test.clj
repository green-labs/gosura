(ns gosura.auth-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [failjure.core :as f]
            [gosura.auth :as gosura-auth]
            [gosura.edn :refer [read-config]]))

(deftest ->auth-result-test
  (let [auth0        (fn [ctx]
                       (boolean (get-in ctx [:identity :id])))
        auth2        (fn [ctx auth-column-name]
                       (when-let [id (get-in ctx [:identity :id])]
                         {auth-column-name id}))
        unauthorized (f/fail "Unauthorized")]
    (testing "auth가 없을 때, 아무일도 일어나지 않는다"
      (let [auth            nil
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/->auth-result auth ctx)
            expected-result nil]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 fn 일 때, auth0 인증을 통과한 경우 추가 필터는 없다"
      (let [auth            auth0
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/->auth-result auth ctx)
            expected-result nil]
        (is (= result expected-result))))
    (testing "auth의 시그니처가 fn 일 때, auth0 인증에 실패하면 Unauthorized 메시지를 반환한다."
      (let [auth   auth0
            ctx    {}
            result (gosura-auth/->auth-result auth ctx)]
        (is (= result unauthorized))))
    (testing "auth의 시그니처가 (fn keyword) 일 때, auth0은 에러를 내뱉는다"
      (let [auth [auth0 :user-id]
            ctx  {:identity {:id "1"}}]
        (is (thrown? clojure.lang.ArityException (gosura-auth/->auth-result auth ctx)))))
    (testing "auth의 시그니처가 (fn keyword) 일 때, auth2 인증에 실패하면 Unauthorized 메세지를 반환한다"
      (let [auth   [auth2 :user-id]
            ctx    {}
            result (gosura-auth/->auth-result auth ctx)]
        (is (= result unauthorized))))
    (testing "auth의 시그니처가 (fn keyword) 일 때, auth2 인증을 통과한 후 추가 필터는 {keyword 인증-id-값}을 잘 반환한다"
      (let [auth            [auth2 :user-id]
            ctx             {:identity {:id "1"}}
            result          (gosura-auth/->auth-result auth ctx)
            expected-result {:user-id "1"}]
        (is (= result expected-result))))))


(defn get-country-code
  [ctx]
  (get-in ctx [:identity :country-code]))

(deftest filter-opts-test
  (let [filters (-> "test/resources/gosura/sample_resolver_configs.edn"
                    read-config
                    :filters)]
    (testing "fiilters.country 설정에 signiture가 싱글 파라미터(qualified symbol)일 때, ctx 내에 인증 정보를 잘 가지고 온다"
      (let [ctx             {:identity {:country-code "JP"}}
            result          (gosura-auth/config-filter-opts filters ctx)
            expected-result {:country-code  "JP"
                             :language-code nil}]
        (is (= result expected-result))))
    (testing "filters.language 설정에 signiture가 coll일 때, ctx 내에 인증 정보를 잘 가지고 온다"
      (let [ctx             {:identity {:language-code "ja"}}
            result          (gosura-auth/config-filter-opts filters ctx)
            expected-result {:country-code  nil
                             :language-code "ja"}]
        (is (= result expected-result))))
    (testing "인증 정보에 국가 정보가 없을 때 빈 정보를 가지고 있다"
      (let [ctx             {}
            result          (gosura-auth/config-filter-opts filters ctx)
            expected-result {:country-code  nil
                             :language-code nil}]
        (is (= result expected-result))))))

(comment
  (run-tests))
