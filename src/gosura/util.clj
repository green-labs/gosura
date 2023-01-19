(ns gosura.util
  (:require [camel-snake-kebab.extras :as cske]
            [clojure.string :refer [ends-with?]]
            [com.walmartlabs.lacinia.resolve :refer [is-resolver-result?]]
            [com.walmartlabs.lacinia.select-utils :refer [is-wrapped-value?]]
            [gosura.csk :as csk]
            [sentry-clj.core :as sentry]))

(defn keyword-vals->string-vals
  "hash-map value 값에 keyword가 있으면 String으로 변환해준다
   
   사용예시) enum 값 때문에 keyword가 들어올 일이 있음
   한계: 1 depth 까지만 적용
   
   TODO) 조금 더 발전 시켜서 defresolver나 resolve-xxx 에서 무조건 이 로직을 타도록 하는 것도 좋을 듯"
  [hash-map]
  (when (map? hash-map)
    (reduce-kv (fn [m key val]
                 (cond (keyword? val) (assoc m key (name val))
                       (and (coll? val) (some keyword? val)) (assoc m key (map name val))
                       :else (assoc m key val))) {} hash-map)))

(defn transform-keys->kebab-case-keyword
  "재귀적으로 form 안에 포함된 모든 key를 camelCase keyword로 변환한다"
  [form]
  (cske/transform-keys csk/->kebab-case-keyword form))

(defn transform-keys->camelCaseKeyword
  "재귀적으로 form 안에 포함된 모든 key를 camelCase keyword로 변환한다"
  [form]
  (cske/transform-keys csk/->camelCaseKeyword form))

(defn send-sentry-server-event
  [event]
  (sentry/send-event (merge (:tags {:branch (:name event)})
                            event)))

(defn update-resolver-result
  "lacinia resolver의 리턴 값에서, 에러를 제외한 데이터 부분에 대해서 함수를 적용시켜 업데이트해 줍니다.
  `resolver-result`의 타입은 순수한 map일수도, WrappedValue일수도, ResolverResultImpl일 수도 있습니다.
  각 타입별로 resolver result 안에 데이터가 들어가 있는 위치가 다르므로, 그에 맞게 적절한 위치를 찾아서 update-fn 함수를 적용해 줍니다.
  "
  [resolver-result update-fn]
  (cond
    (is-resolver-result? resolver-result) (update-in resolver-result [:resolved-value :value] update-fn)
    (is-wrapped-value? resolver-result) (update-in resolver-result [:value] update-fn)
    :else (update-fn resolver-result)))

(defn update-vals-having-keys
  "m: map(데이터)
   ks: 특정 키값
   f: val 업데이트 함수"
  [m ks f]
  (reduce #(update %1 %2 f) m ks))

(defn update-existing
  ([m k f]
   (if-let [kv (find m k)] (assoc m k (f (val kv))) m))
  ([m k f x]
   (if-let [kv (find m k)] (assoc m k (f (val kv) x)) m))
  ([m k f x y]
   (if-let [kv (find m k)] (assoc m k (f (val kv) x y)) m))
  ([m k f x y z]
   (if-let [kv (find m k)] (assoc m k (f (val kv) x y z)) m))
  ([m k f x y z & more]
   (if-let [kv (find m k)] (assoc m k (apply f (val kv) x y z more)) m)))

(defn stringify-ids
  "행의 ID들(열이 id, 그리고 -id로 끝나는 것들)을 문자열로 변경한다."
  [row]
  (-> row
      (update :id str)
      (update-vals-having-keys (->> (keys row)
                                    (filter #(ends-with? (name %) "-id")))
                               #(some-> % str)))) ; *-id 컬럼 값이 nil일 수 있기 때문에, nil이 아닐 때에만 str을 적용

(defmulti
  ^{:deprecated "0.2.8"}
  qualified-symbol->requiring-var!
  (fn [x]
    (type x)))

(defn requiring-var!
  "qualified-symbol을 var로 변환합니다"
  [sym|var]
  (cond
    (symbol? sym|var) (requiring-resolve sym|var)
    (var? sym|var) sym|var
    :else (prn "something wrong")))

(defmethod qualified-symbol->requiring-var! :default
  [x]
  x)

(defmethod qualified-symbol->requiring-var! clojure.lang.PersistentVector
  [coll]
  (reduce (fn [acc value]
            (if (qualified-symbol? value)
              (conj acc (requiring-var! value))
              (merge acc value)))
          []
          coll))

(defmethod qualified-symbol->requiring-var! clojure.lang.IPersistentMap
  [m]
  (reduce (fn [acc [key value]]
            (if (qualified-symbol? value)
              (merge acc {key (requiring-var! value)})
              (merge acc {key value})))
          {}
          m))

(defmethod qualified-symbol->requiring-var! clojure.lang.Symbol
  [sym]
  (requiring-var! sym))
