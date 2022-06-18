(ns gosura.cli
  (:require [cli-matic.core :refer [run-cmd]]))

(defn generate-query [{:keys [path]}]
  (prn "generate query" path))

(def config 
  {:command     "gosura-cli"
   :description "A simple gosura cli"
   :version     "0.0.1"
   :subcommands [{:command     "generate"
                  :short        "g"
                  :description "Generates query"
                  :subcommands [{:command     "query"
                                 :description "Query"
                                 :opts        [{:as      "Path to generate"
                                                :option  "path"
                                                :default :present
                                                :type    :string}]
                                 :runs generate-query}]}]})

(defn -main
  [& args]
  (run-cmd args config))

(comment
  (-main))
