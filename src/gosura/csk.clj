(ns gosura.csk
  (:require [clojure.walk :as walk])
  (:import [clojure.lang Keyword]
           [com.google.common.base CaseFormat]))

(def camelCase CaseFormat/LOWER_CAMEL)
(def PascalCase CaseFormat/UPPER_CAMEL)
(def kebab-case CaseFormat/LOWER_HYPHEN)
(def snake_case CaseFormat/LOWER_UNDERSCORE)
(def UPPER_SNAKE_CASE CaseFormat/UPPER_UNDERSCORE)

(defn transform [^CaseFormat from ^CaseFormat to ^String s]
  (.to from to s))

(defn transform-keyword [^CaseFormat from ^CaseFormat to ^Keyword k]
  (keyword (transform from to (name k))))

(defn camelCase->kebab-case [^String s]
  (transform camelCase kebab-case s))

(defn camelCaseKeyword->kebab-case-keyword [^Keyword k]
  (keyword (camelCase->kebab-case (name k))))

(defn kebab-case->camelCase [^String s]
  (.to CaseFormat/LOWER_HYPHEN
       CaseFormat/LOWER_CAMEL s))

(defn kebab-case-keyword->camelCaseKeyowrd [^Keyword k]
  (keyword (kebab-case->camelCase (name k))))

(defn snake_case->kebab-case [^String s]
  (.to CaseFormat/LOWER_UNDERSCORE
       CaseFormat/LOWER_HYPHEN s))

(defn snake_case_keyword->kebab-case-keyword [^Keyword k]
  (keyword (snake_case->kebab-case (name k))))

(defn kebab-case->snake_case [^String s]
  (.to CaseFormat/LOWER_HYPHEN
       CaseFormat/LOWER_UNDERSCORE s))

(defn kebab-case-keyword->snake_case_keyword [^Keyword k]
  (keyword (kebab-case->snake_case (name k))))

(defn kebab-case->PascalCase [^String s]
  (.to CaseFormat/LOWER_HYPHEN
       CaseFormat/UPPER_CAMEL s))

(defn kebab-case-keyword->PascalCaseKeyword [^Keyword k]
  (keyword (kebab-case->PascalCase (name k))))

(defn transform-keys
  "Recursively transforms all map keys in coll with t."
  [t coll]
  (letfn [(transform [[k v]] [(t k) v])]
    (walk/postwalk (fn [x] (if (map? x) (with-meta (into {} (map transform x)) (meta x)) x)) coll)))

(defn transform-keys-camelCase->kebab-case [m]
  (transform-keys camelCaseKeyword->kebab-case-keyword m))

(defn transform-keys-kebab-case->camelCase [m]
  (transform-keys kebab-case-keyword->camelCaseKeyowrd m))

(defn transform-keys-snake_case_->kebab-case [m]
  (transform-keys snake_case_keyword->kebab-case-keyword m))
