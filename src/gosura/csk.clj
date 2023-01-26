(ns gosura.csk
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.memoize :as m]
            [clojure.string :as string]))

(def ->kebab-case
  (memoize csk/->kebab-case))

(defn- request-camelCase->kebab-case-keyword
  [s]
  (let [s (name s)]
    (keyword (string/replace s #"[A-Z]" #(str "-" (string/lower-case %))))))

(defn- request-kebab-case->camelCaseKeyword
  [s]
  (let [s (name s)]
    (keyword (string/replace s #"-([a-z])" #(string/upper-case (second %))))))

(def camelCase->kebab-case-keyword
  (memoize request-camelCase->kebab-case-keyword))

(def kebab-case->camelCaseKeyword
  (memoize request-kebab-case->camelCaseKeyword))

(def ->kebab-case-keyword
  (memoize csk/->kebab-case-keyword))

(def ->kebab-case-string
  (memoize csk/->kebab-case-string))

(def ->PascalCaseString
  (memoize csk/->PascalCaseString))

(def ->PascalCaseKeyword
  (memoize csk/->PascalCaseKeyword))

(def ->PascalCase
  (memoize csk/->PascalCase))

(def ->snake_case
  (memoize csk/->snake_case))

(def ->snake_case_keyword
  (memoize csk/->snake_case_keyword))

(def ->camelCaseKeyword
  (memoize csk/->camelCaseKeyword))
