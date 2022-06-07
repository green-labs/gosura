(ns gosura.util
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [sentry-clj.core :as sentry]
            [com.walmartlabs.lacinia.resolve :refer [is-resolver-result?]]
            [com.walmartlabs.lacinia.select-utils :refer [is-wrapped-value?]]))

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
