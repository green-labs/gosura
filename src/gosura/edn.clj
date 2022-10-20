(ns gosura.edn
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(defn read-config
  "edn 형식의 설정파일을 불러들임.
  tagged literal `#var`를 지원하기 위해 사용한다.
  "
  [path]
  (with-open [r (io/reader path)]
    (edn/read {:readers (merge default-data-readers
                               {'var requiring-resolve})}
              (PushbackReader. r))))
