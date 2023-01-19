(ns gosura.helpers.node
  (:require [com.walmartlabs.lacinia.schema :as schema]
            [gosura.csk :as csk]))

(defn tag-with-subtype
  [{:keys [subtype] :as row} subtype->node-type]
  (let [node-type (get subtype->node-type subtype)]
    (-> row
        (assoc :node-type node-type)
        (schema/tag-with-type (csk/->PascalCaseKeyword node-type)))))
