(ns gosura.helpers.relay-test
  (:require [clojure.test :refer [are deftest is testing run-tests]]
            [greenlabs.gosura.helpers.relay :as gosura-relay]))

(deftest encode-id-test
  (testing "(node-type, db-id)의 map을 입력받아 Base64 문자열의 Relay 노드 Id로 인코딩합니다."
    (let [node-map {:node-type :test
                    :db-id     1}
          result (gosura-relay/encode-id node-map)]
      (is (= result "dGVzdDox"))))
  (testing "Node 레코드(node-type, db-id)를 입력받아 Base64 문자열의 Relay 노드 Id로 인코딩합니다."
    (let [node-record (gosura-relay/->node :test 1)
          result (gosura-relay/encode-id node-record)]
      (is (= result "dGVzdDox"))))
  (testing "잘못된 레코드가 입력되었을 때, 에러를 잘 뱉어낸다"
    (let [node-record {:node-type :test
                       :uid 1}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid node schema" (gosura-relay/encode-id node-record))))))

(deftest decode-id-test
  (testing "Base64 인코딩된 ID를 Node 레코드(node-type, db-id)로 디코드합니다"
    (let [encoded-id "dGVzdDox"
          result (gosura-relay/decode-id encoded-id)
          expected-result (gosura-relay/->node :test 1)]
      (is (= result expected-result))))
  (testing "Base64 인코딩된 값이 Node가 아닐 때, 에러를 잘 뱉어낸다"
    (let [encoded-id "+/ss"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid node id" (gosura-relay/decode-id encoded-id)))))
  (testing "str이 아닌 타입이 입력되었을 때, 에러를 잘 뱉어낸다"
    (let [encoded-id 1]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"node id must be string" (gosura-relay/decode-id encoded-id))))))

(deftest ->node-test
  (testing "node타입의 데이터로 변환하는 데이터의 형태는 map이여야한다"
    (let [data "data"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"data to convert must be map" (gosura-relay/encode-node-id :test data)))))
  (testing "node타입의 데이터로 map에 id가 포함하는 데이터가 있을 때 id가 잘 인코딩된다"
    (let [data {:id 1
                :value 1}
          node-type :test
          result (gosura-relay/encode-node-id node-type data)
          expected-result {:id "dGVzdDox"
                           :value 1}]
      (is (= result expected-result)))))

(deftest extend-relay-types-test
  (testing "lacinia schema에서 정의한 Node implements 하나만 된 objects들에게 Graphql relay spec에 맞는 edges, connections를 추가해줍니다"
    (let [schema {:objects {:testObject {:implements  [:Node]
                                         :description "test description"
                                         :fields      {:testField {:description "test field"
                                                                   :type        '(non-null Int)}}}}}
          result (gosura-relay/extend-relay-types schema)
          expected-result {:objects {:testObject           {:implements  [:Node]
                                                            :description "test description"
                                                            :fields      {:testField {:description "test field"
                                                                                      :type        '(non-null Int)}}}
                                     :testObjectEdge       {:implements [:Edge]
                                                            :fields     {:cursor {:type '(non-null String)}
                                                                         :node   {:type '(non-null :testObject)}}}
                                     :testObjectConnection {:implements [:Connection]
                                                            :fields     {:edges    {:type '(non-null (list (non-null :testObjectEdge)))}
                                                                         :pageInfo {:type '(non-null :PageInfo)}
                                                                         :count    {:type '(non-null Int)}}}}}]
      (is (= result expected-result))))
  (testing "lacinia schema에서 정의한 objects가 Node implements가 아닌 경우에는 아무일도 일어나지 않는다"
    (let [schema {:objects {:testObject {:description "test description"
                                         :fields      {:testField {:description "test field"
                                                                   :type        '(non-null Int)}}}}}
          result (gosura-relay/extend-relay-types schema)
          expected-result schema]
      (is (= result expected-result))))
  (testing "lacinia schema에서 정의한 objects가 Node와 다른 implements가 같이 있을 때에 Graphql relay spec에 맞는 edges, connections를 추가해줍니다"
    (let [schema {:objects {:testObject {:implements  [:Person :Node]
                                         :description "test description"
                                         :fields      {:testField {:description "test field"
                                                                   :type        '(non-null Int)}}}}}
          result (gosura-relay/extend-relay-types schema)
          expected-result {:objects {:testObject           {:implements  [:Person :Node]
                                                            :description "test description"
                                                            :fields      {:testField {:description "test field"
                                                                                      :type        '(non-null Int)}}}
                                     :testObjectEdge       {:implements [:Edge]
                                                            :fields     {:cursor {:type '(non-null String)}
                                                                         :node   {:type '(non-null :testObject)}}}
                                     :testObjectConnection {:implements [:Connection]
                                                            :fields     {:edges    {:type '(non-null (list (non-null :testObjectEdge)))}
                                                                         :pageInfo {:type '(non-null :PageInfo)}
                                                                         :count    {:type '(non-null Int)}}}}}]
      (is (= result expected-result))))
  (testing "lacinia schema에서 objects가 아닌 다른 타입의 경우에는 아무일도 일어나지 않는다"
    (let [schema {:objects       {:testObject {:implements  [:Node]
                                               :description "test description"
                                               :fields      {:testField {:description "test field"
                                                                         :type        '(non-null Int)}}}}
                  :enums         {:testEnum {:values [{:enum-value  :OTHER
                                                       :description "기타"}]}}
                  :interfaces    {:TestInterface {:fields {:id {:type '(non-null ID)}}}}
                  :queries       {:testQuery {:type    '(non-null :testObject)
                                              :resolve :test-resolver}}
                  :unions        {:testUnion {:members [:testObject :testEnum]}}
                  :input-objects {:testInputObject {:fields {:status {:type '(non-null String)}}}}
                  :mutations     {:createRfqRequest {:type    '(non-null :testUnion)
                                                     :args    {:input {:type '(non-null :testInputObject)}}
                                                     :resolve :test-resolver-1}}}
          result (gosura-relay/extend-relay-types schema)
          expected-result (assoc schema :objects {:testObject           {:implements  [:Node]
                                                                         :description "test description"
                                                                         :fields      {:testField {:description "test field"
                                                                                                   :type        '(non-null Int)}}}
                                                  :testObjectEdge       {:implements [:Edge]
                                                                         :fields     {:cursor {:type '(non-null String)}
                                                                                      :node   {:type '(non-null :testObject)}}}
                                                  :testObjectConnection {:implements [:Connection]
                                                                         :fields     {:edges    {:type '(non-null (list (non-null :testObjectEdge)))}
                                                                                      :pageInfo {:type '(non-null :PageInfo)}
                                                                                      :count    {:type '(non-null Int)}}}})]
      (is (= result expected-result)))))

(deftest build-page-options-test
  (testing "아무 조건도 없을 때 기본값으로 다 가지고 온다"
    (let [args {}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                12}]
      (is (= result expected-result))))
  (testing "first만 있을 때 first 개수 + 2만큼 limit이 잘 설정된다"
    (let [args {:first 3}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                5}]
      (is (= result expected-result))))
  (testing "order-by만 있을 때 설정이 잘 된다"
    (let [args {:order-by :value}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :value
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                12}]
      (is (= result expected-result))))
  (testing "order-direction만 있을 때 설정이 잘 된다"
    (let [args {:order-direction :desc}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :desc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                12}]
      (is (= result expected-result))))
  (testing "order-direction, order-by가 같이 있을 때,설정이 잘 된다"
    (let [args {:order-direction :desc :order-by :value}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :value
                           :order-direction      :desc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                12}]
      (is (= result expected-result))))
  (testing "order-direction에 이상한 값이 들어와도 asc 기본값으로 잘 설정된다"
    (let [args {:order-direction :up}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                12}]
      (is (= result expected-result))))
  (testing "first가 있고 order-direction이 asc일 때, order-direction은 asc이다"
    (let [args {:first 3
                :order-direction :asc}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                5}]
      (is (= result expected-result))))
  (testing "first가 있고 order-direction이 desc일 때, order-direction은 desc이다"
    (let [args {:first 3
                :order-direction :desc}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :desc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                5}]
      (is (= result expected-result))))
  (testing "last가 있고 order-direction이 asc일 때, order-direction은 desc이다"
    (let [args {:first 3
                :order-direction :desc}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :desc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                5}]
      (is (= result expected-result))))
  (testing "last가 있고 order-direction이 desc일 때, order-direction은 asc이다"
    (let [args {:first 3
                :order-direction :asc}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            nil
                           :cursor-ordered-value nil
                           :limit                5}]
      (is (= result expected-result))))
  (testing "first-after 조합일 때 cursor-id와 cursor-ordered-value를 잘 설정한다"
    (let [args {:first 3
                :after "TlBZAHFkAW4BZAE="}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :asc
                           :page-direction       :forward
                           :cursor-id            1
                           :cursor-ordered-value 1
                           :limit                5}]
      (is (= result expected-result))))
  (testing "last-before 조합일 때 cursor-id와 cursor-ordered-value를 잘 설정한다"
    (let [args {:last 3
                :before "TlBZAHFkAW4BZAE="}
          result (gosura-relay/build-page-options args)
          expected-result {:order-by             :id
                           :order-direction      :desc
                           :page-direction       :backward
                           :cursor-id            1
                           :cursor-ordered-value 1
                           :limit                5}]
      (is (= result expected-result))))
  (testing "first-before 조합은 유효하지 않은 호출이다"
    (let [args {:first  3
                :before "TlBZAHFkAW4BZAE="}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"first & before must not be input together." (gosura-relay/build-page-options args)))))
  (testing "last-after 조합은 유효하지 않은 호출이다"
    (let [args {:last  3
                :after "TlBZAHFkAW4BZAE="}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"last & after must not be input together." (gosura-relay/build-page-options args)))))
  (testing "first-last 조합은 유효하지 않은 호출이다"
    (let [args {:first 3
                :last  3}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"first & last must not be input together." (gosura-relay/build-page-options args)))))
  (testing "after-before 조합은 유효하지 않은 호출이다"
    (let [args {:after  "TlBZAHFkAW4BZAE="
                :before "TlBZAHFkAW4BZAE="}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"after & before must not be input together." (gosura-relay/build-page-options args))))))

(deftest build-connection-test
  (let [rows [{:id    1
               :value 1}
              {:id    2
               :value 2}
              {:id    3
               :value 3}
              {:id    4
               :value 4}
              {:id    5
               :value 5}
              {:id    6
               :value 6}
              {:id    7
               :value 7}
              {:id    8
               :value 8}
              {:id    9
               :value 9}
              {:id    10
               :value 10}]
        node-type :test
        third #(second (next %))]
    (testing "GraphQL connection 스펙에 맞는 정보를 잘 반환하는 테스트 중"
      (testing "정배열-정방향일 때 조회가 제대로 된다"
        (let [order-by       :id
              page-direction :forward
              limit          3
              cursor-id      nil
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id rows)]
          (are [expected target] (= expected target)
            (-> result :edges first :node) {:id    "dGVzdDox"
                                            :value 1}
            (-> result :edges second :node) {:id    "dGVzdDoy"
                                             :value 2}
            (-> result :edges third :node) {:id    "dGVzdDoz"
                                            :value 3})))
      (testing "정배열-역방향일 때 조회가 제대로 된다"
        (let [order-by       :id
              page-direction :backward
              limit          3
              cursor-id      nil
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id rows)]
          (are [expected target] (= expected target)
            (-> result :edges first :node) {:id    "dGVzdDoz"
                                            :value 3}
            (-> result :edges second :node) {:id    "dGVzdDoy"
                                             :value 2}
            (-> result :edges third :node) {:id    "dGVzdDox"
                                            :value 1})))
      (testing "역배열-정방향일 때 조회가 제대로 된다"
        (let [order-by       :id
              page-direction :forward
              limit          3
              cursor-id      nil
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id (reverse rows))]
          (are [expected target] (= expected target)
            (-> result :edges first :node) {:id    "dGVzdDoxMA=="
                                            :value 10}
            (-> result :edges second :node) {:id    "dGVzdDo5"
                                             :value 9}
            (-> result :edges third :node) {:id    "dGVzdDo4"
                                            :value 8})))
      (testing "역배열-역방향일 때 조회가 제대로 된다"
        (let [order-by       :id
              page-direction :backward
              limit          3
              cursor-id      nil
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id (reverse rows))]
          (are [expected target] (= expected target)
            (-> result :edges first :node) {:id    "dGVzdDo4"
                                            :value 8}
            (-> result :edges second :node) {:id    "dGVzdDo5"
                                             :value 9}
            (-> result :edges third :node) {:id    "dGVzdDoxMA=="
                                            :value 10})))
      (testing "다음 페이지가 없을 때 처리가 잘 된다"
        (let [order-by       :id
              page-direction :forward
              limit          3
              cursor-id      10
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id rows)]
          (are [expected target] (= expected target)
            (-> result :page-info :has-previous-page) true
            (-> result :page-info :has-next-page) false
            (-> result :edges) [])))
      (testing "이전 페이지가 없을 때, 즉, cursor가 없을 때 처음부터 조회를 한다"
        (let [order-by       :id
              page-direction :forward
              limit          3
              cursor-id      nil ; cursor가 없을 때
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id rows)]
          (are [expected target] (= expected target)
            (-> result :page-info :has-previous-page) false
            (-> result :page-info :has-next-page) true
            (-> result :edges first :node) {:id    "dGVzdDox"
                                            :value 1}
            (-> result :edges second :node) {:id    "dGVzdDoy"
                                             :value 2}
            (-> result :edges third :node) {:id    "dGVzdDoz"
                                            :value 3})))
      (testing "조회된 데이터의 수와 count의 수가 잘 맞다"
        (let [order-by       :id
              page-direction :forward
              limit          3]
          (are [expected target] (= expected
                                    target)
            (:count (gosura-relay/build-connection node-type order-by page-direction limit nil rows))
            3

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 3 rows))
            3

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 5 rows))
            3

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 7 rows))
            3

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 8 rows))
            2

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 9 rows))
            1

            (:count (gosura-relay/build-connection node-type order-by page-direction limit 10 rows))
            0)))
      (testing "데이터(node)의 id가 base64 인코딩된 정보이다"
        (let [order-by       :id
              page-direction :forward
              limit          3
              cursor-id      nil
              result         (gosura-relay/build-connection node-type order-by page-direction limit cursor-id rows)]
          (are [expected target] (= expected target)
            (-> result :edges first :node :id gosura-relay/decode-id) (gosura-relay/->node :test 1)
            (-> result :edges second :node :id gosura-relay/decode-id) (gosura-relay/->node :test 2)
            (-> result :edges third :node :id gosura-relay/decode-id) (gosura-relay/->node :test 3)))))))

(comment
  (run-tests))
