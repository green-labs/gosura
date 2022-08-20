(ns gosura.edn-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [gosura.edn]))

(deftest read-config-test
  (testing "#var tag가 있을 경우 edn 파일을 읽은 후 symbol을 var로 변환한다."
    (let [my-config (gosura.edn/read-config "test/resources/gosura/tagged_literal_config.edn")]
      (is (var? (:fn1 my-config)))
      (is (symbol? (:fn2 my-config)))
      (is (= 2 ((:fn3 my-config) 1))))))

(comment
  (run-tests))
