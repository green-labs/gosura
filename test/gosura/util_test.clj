(ns gosura.util-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [gosura.edn]
            [gosura.util :as util]))

(deftest requiring-var!-test
  (testing "argument에 var가 들어오면 var를 그대로 반환한다"
    (let [my-config (gosura.edn/read-config "test/resources/gosura/tagged_literal_config.edn")]
      (is (var? (util/requiring-var! (:fn1 my-config))))))
  (testing "argument에 symbol이 들어오면 var를 변환한다"
    (let [my-config (gosura.edn/read-config "test/resources/gosura/tagged_literal_config.edn")]
      (is (var? (util/requiring-var! (:fn2 my-config))))))
  (testing "argument에 var도 symbol이 아닌 값이 들어오면 nil을 반환한다"
    (let [my-config (gosura.edn/read-config "test/resources/gosura/tagged_literal_config.edn")]
      (is (nil? (util/requiring-var! (:fn4 my-config)))))))

(comment
  (run-tests))
