(ns gosura.helpers.node
  (:require [camel-snake-kebab.core :as csk]
            [com.walmartlabs.lacinia.schema :as schema]))

(defn tag-with-subtype
  [{:keys [subtype] :as row} subtype->node-type]
  (let [node-type (get subtype->node-type subtype)]
    (-> row
        (assoc :node-type node-type)
        (schema/tag-with-type (csk/->PascalCaseKeyword node-type)))))
