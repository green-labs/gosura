(ns gosura.helpers.error
  (:require [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [gosura.helpers.error :as error]
            [io.aviso.exception :as e]))

(defn error
  "resolve-as를 사용하여 error를 반환하고 기본적으로 값으로는 nil을 반환하도록 한다"
  ([resolver-errors]
   (error nil resolver-errors))
  ([resolved-value resolver-errors]
   (resolve-as resolved-value resolver-errors)))

(defn- stack-trace->formatted
  [{:keys [formatted-name file line]}]
  (if line
    (str formatted-name " (" file ":" line ")")
    formatted-name))

(defn analyzed->formatted [analyses]
  (update analyses :stack-trace
          (fn [stack-trace]
            (when stack-trace (map stack-trace->formatted stack-trace)))))

(defn- extract-fn-name [clj-classname]
  (->> (string/split clj-classname #"\$")
       (map e/demangle)
       (string/join "/")))

(defn- take-primary-analyze [analyses]
  (->> analyses
       (drop-while #(not (:stack-trace %)))
       first
       :stack-trace
       first))

(defn primary-trace [analyses]
  (let [primary-analyze (take-primary-analyze analyses)]
    {:in   (extract-fn-name (:class primary-analyze))
     :line (:line primary-analyze)}))
