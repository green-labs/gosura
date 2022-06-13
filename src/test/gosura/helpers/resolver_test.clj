(ns gosura.helpers.resolver-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [gosura.helpers.resolver :as gosura-resolver]))

(deftest resolve-connection-test)

(deftest resolve-by-fk-test)

(deftest resolve-by-parent-pk-test)

(deftest resolve-connection-by-fk)

(deftest resolve-connection-by-pk-list-test)

(comment
  (run-tests))
