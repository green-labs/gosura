(ns gosura.edn
  (:require [aero.core :as aero]))

(defmethod aero/reader 'var
  [_opts _tag value]
  (requiring-resolve value))

(defn read-config
  [path]
  (aero/read-config path))
