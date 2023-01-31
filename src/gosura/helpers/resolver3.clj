(ns gosura.helpers.resolver3
  "gosura.helpers.resolver의 v3입니다."
  (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [gosura.helpers.error :as ghe]
            [gosura.helpers.relay :as ghr]
            [gosura.helpers.superlifter :refer [->lacinia-promise]]
            [gosura.util :as gu]
            [promesa.core :as prom]
            [taoensso.timbre :as log]))

(defn args->kebab-case-args
  [{:keys [ctx args parent]}]
  {:ctx    ctx
   :args   (gu/transform-keys->kebab-case-keyword args)
   :parent (gu/transform-keys->kebab-case-keyword parent)})

(defn decode-global-ids-by-keys-in-args
  "arguments 맵에서 ks의 키값 값을 재귀적으로 찾아 DB ID로 디코드합니다."
  [ks {:keys [ctx args parent]}]
  (let [ks    (set ks)
        args' (reduce-kv (fn [m k v]
                           (cond
                             (ks k) (assoc m k (if (coll? v)
                                                 (map ghr/decode-global-id->db-id v)
                                                 (ghr/decode-global-id->db-id v)))
                             (associative? v) (assoc m k (ghr/decode-global-ids-by-keys v ks))
                             :else (assoc m k v)))
                         {}
                         args)]
    {:ctx    ctx
     :args   args'
     :parent parent}))

(defn parse-fdecl
  "함수의 이름 뒤에 오는 선언부를 파싱합니다. doc-string와 option이 있을 수도 있고, 없을 수도 있기 때문에 그를 적절히 파싱해 줍니다.
  (fdecl이라는 이름은 core의 defn 구현에서 쓰이는 이름을 따왔습니다)
  "
  [fdecl]
  (let [[doc-string args]      (if (string? (first fdecl))
                                 [(first fdecl) (rest fdecl)]
                                 [nil fdecl])
        [option [args & body]] (if (map? (first args))
                                 [(first args) (rest args)]
                                 [{} args])]
    {:doc-string doc-string
     :option     option
     :args       args
     :body       body}))

(defn transform-result
  [body]
  (gu/update-resolver-result body gu/transform-keys->camelCaseKeyword))

(defn wrap-async-body
  [body]
  `(let [result# (resolve/resolve-promise)]
     (prom/future
       (try
         (resolve/deliver! result# (transform-result ~body))
         (catch Throwable t#
           (resolve/deliver! result# nil (ghe/error-response t#)))))
     result#))

(defn wrap-catch-body
  [body]
  `(try
     ~body
     (catch Exception e#
       (log/error e#)
       (resolve/resolve-as
        nil
        (ghe/error-response e#)))))

(defmacro defresolver
  {:arglists '([name doc-string? option? args & body])}
  [name & fdecl]
  (let [{:keys [doc-string option args body]}  (parse-fdecl fdecl)
        {:keys [pre-opts body-opts post-opts]} option
        body-opts-applied                      (reduce (fn [acc1# f1#]
                                                         ((resolve f1#) acc1#))
                                                       `(do ~@body)
                                                       body-opts)
        body-fn                                `(fn ~args ~body-opts-applied)
        header                                 (if doc-string [name doc-string] [name])]
    `(defn ~@header [ctx# arg# parent#]
       (let [info#             (reduce (fn [acc0# f0#]
                                         (f0# acc0#))
                                       {:ctx    ctx#
                                        :args   arg#
                                        :parent parent#}
                                       ~pre-opts)
             ctx'#             (:ctx info#)
             args'#            (:args info#)
             parent'#          (:parent info#)
             result#           (~body-fn ctx'# args'# parent'#)
             apply-post-ops-fn# (fn [result'#] (reduce (fn [acc2# f2#]
                                                         (f2# acc2#))
                                                       result'#
                                                       ~post-opts))]
         (if (instance? java.util.concurrent.CompletableFuture result#)
           (-> (prom/then result# apply-post-ops-fn#)
               ->lacinia-promise)
           (apply-post-ops-fn# result#))))))

(comment
  (defresolver test-resolver-post-opts
    {:pre-opts  [args->kebab-case-args]
     :body-opts [wrap-catch-body]
     :post-opts [transform-result]}
    [{:keys [ddb] :as a} b c]
    b)
  (test-resolver-post-opts {:a "a"} {:myId 1} {})

  (macroexpand-1 '(defresolver test-resolver-post-opts
                    {:pre-opts  []
                     :body-opts [wrap-catch-body]
                     :post-opts [transform-result]}
                    [{:keys [ddb] :as a} b c]
                    b))

  (defresolver test-resolver-pre-opts-decode-global-ids
    {:pre-opts  []
     :body-opts [wrap-catch-body]
     :post-opts [transform-result]}
    [{:keys [ddb] :as a} b c]
    a)
  (test-resolver-pre-opts-decode-global-ids {:ddb "test"} {:myId "bm90aWNlOjI="} {})

  (macroexpand-1 '(defresolver3 test-resolver-pre-opts-decode-global-ids
                    {:pre-opts  []
                     :body-opts [wrap-catch-body]
                     :post-opts [transform-result]}
                    [{:keys [ddb]} b c]))

  (require '[gosura.helpers.resolver2 :as gosura-resolver])
  (gosura-resolver/defresolver test2-resolver
    {:async? true}
    [ctx args parent]
    {:res 1})
  `(inc 1)
  (test2-resolver {} {} {}))
