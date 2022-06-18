(ns gosura.cli
  (:require [cli-matic.core :refer [run-cmd]]))

(defn generate-query [{:keys [path name]}]
  (let [path (str path "/" name "/")]
    (.mkdir (java.io.File. path))

    (spit (str path "superfetcher.clj") "")
    (spit (str path "db.clj") "")
    (spit (str path "resolve.clj") "")

    (println (str "Query " name " generated."))))

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
                                                :type    :string}
                                               {:as      "Query name"
                                                :option  "name"
                                                :default :present
                                                :type    :string}]
                                 :runs generate-query}]}]})

(defn -main
  [& args]
  (run-cmd args config))

(comment
  (-main))
