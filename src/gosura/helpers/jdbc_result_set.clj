(ns gosura.helpers.jdbc-result-set
  (:require [gosura.case-format :as cf]
            [next.jdbc.result-set :as rs :refer [as-modified-maps
                                                 as-unqualified-modified-maps]]))

(defn as-kebab-maps
  [rs opts]
  (as-modified-maps rs (assoc opts
                              :qualifier-fn cf/snake_case->kebab-case
                              :label-fn cf/snake_case->kebab-case)))

(defn as-unqualified-kebab-maps
  [rs opts]
  (as-unqualified-modified-maps rs (assoc opts :label-fn cf/snake_case->kebab-case)))
