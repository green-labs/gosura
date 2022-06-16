(ns gosura.auth
  (:require [clojure.core.match :refer [match]]
            [failjure.core :as f]
            [gosura.util :as util]))

(defn- var-fn?
  "v가 var이고, 함수를 가리킨다면 true를 반환합니다.
  이외에는 false를 반환합니다."
  [v]
  (and (var? v) (fn? (var-get v))))

(defn process-auth-fn
  "auth: auth 혹은 [fn & args]
  auth가 fn인 경우, (fn ctx)를 반환합니다
  auth가 [fn & args] 형태의 시퀀스인 경우, (apply fn ctx args)를 반환합니다. (thread-first와 유사)
  "
  [auth ctx]
  (match [auth]
    [(_ :guard nil?)] true
    [(auth-fn :guard var-fn?)] (auth-fn ctx)
    [(auth-fn :guard fn?)] (auth-fn ctx)
    [([(auth-fn :guard var-fn?) & args] :seq)] (apply auth-fn ctx args)
    [([(auth-fn :guard fn?) & args] :seq)] (apply auth-fn ctx args)))

(defn ->auth-result
  [auth ctx]
  (let [result (process-auth-fn auth ctx)]
    (cond
      (true? result) nil
      (false? (boolean result)) (f/fail "Unauthorized")
      :else result)))

(defn config-filter-opts
  [filters ctx]
  (reduce (fn [acc [key value]]
            (merge acc {key (process-auth-fn (util/qualified-symbol->requiring-var! value) ctx)}))
          {}
          filters))
