(ns gosura.helpers.node
  (:require [com.walmartlabs.lacinia.schema :as schema]
            [gosura.case-format :as cf]))

(defn tag-with-subtype
  [{:keys [subtype] :as row} subtype->node-type]
  (let [node-type (get subtype->node-type subtype)]
    (-> row
        (assoc :node-type node-type)
        (schema/tag-with-type (cf/kebab-case-keyword->PascalCaseKeyword node-type)))))
