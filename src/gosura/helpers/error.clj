(ns gosura.helpers.error
  (:require [com.walmartlabs.lacinia.resolve :refer [resolve-as]]))

(defn error
  "resolve-as를 사용하여 error를 반환하고 기본적으로 값으로는 nil을 반환하도록 한다"
  ([resolver-errors]
   (error nil resolver-errors))
  ([resolved-value resolver-errors]
   (resolve-as resolved-value resolver-errors)))

(defn error-response
  [throwable]
  {:message    (ex-message throwable)
   :info       (str throwable)
   :type       (.getName (class throwable))
   :stacktrace (->> (.getStackTrace throwable)
                    (map str))})
