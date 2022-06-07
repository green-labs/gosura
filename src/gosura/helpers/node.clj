(ns gosura.helpers.node
  (:require [camel-snake-kebab.core :as csk]
            [com.walmartlabs.lacinia.schema :as schema]))

(defn tag-with-subtype
  [{:keys [subtype] :as row} subtype->node-type]
  (let [node-type (get subtype->node-type subtype)
        added-node-type-row (assoc row :node-type node-type)]
    (schema/tag-with-type added-node-type-row (csk/->PascalCaseKeyword node-type))))
