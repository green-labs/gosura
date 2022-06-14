(ns gosura.builders.resolver
  (:require [clojure.zip :as z]))

(defn- schema-zipper [m]
  (z/zipper
    (fn [x] (or (map? x) (map? (nth x 1))))
    (fn [x] (seq (if (map? x) x (nth x 1))))
    (fn [x children]
      (if (map? x)
        (into {} children)
        (assoc x 1 (into {} children))))
    m))

(defn- ->schema-and-resolvers
  [loc rs]
  (if (z/end? loc)
    {:schema    (z/root loc)
     :resolvers rs}
    (let [k (ffirst loc)
          v (second (first loc))]
      (if (and (= :resolve k) (symbol? v))
        (let [k' (keyword (str v))]
          (recur (-> loc
                     (z/replace [:resolve k'])
                     z/next)
                 (assoc rs k' v)))
        (recur (z/next loc) rs)))))

(defn- require-resolver-namespaces
  [{:keys [resolvers]
    :as   m}]
  ;; import namespaces
  (let [resolver-namespaces (->> (vals resolvers)
                                 (map #(symbol (namespace %))))]
    (doseq [resolver-namespace resolver-namespaces]
      (require resolver-namespace)))

  ;; eval
  (update m :resolvers #(into {} (for [[k v] %]
                                   [k (eval v)]))))

(defn build-resolvers
  "GraphQL 스키마 맵을 순회하며 :resolve 함수를 찾습니다.
   :resolve의 함수 Symbol을 활용해서 새로운 map을 :resolvers에 반환합니다.
   변경된 스키마 맵(:schema)과 새롭게 생성한 리졸버 맵(:resolvers)은
   lacinia의 attach-resolvers 함수에서 사용합니다.

   입력 : schema
   출력 : {:schema {...} :resolvers {...}}

   예)
   {:resolve farmmorning.user/get-address
    ...
    :resolve farmmorning.crop/get-name}
    ->
   {:schema    {:resolve :farmmorning.user/get-address
                :resolve :farmmorning.crop/get-name
    :resolvers {:farmmorning.user/get-address farmmorning.user/get-address
                :farmmorning.crop/get-name farmmorning.crop/get-name}}"
  [schema]
  (-> schema
      schema-zipper
      (->schema-and-resolvers {})
      require-resolver-namespaces))
