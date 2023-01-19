(ns gosura.csk
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.memoize :as m]
            [clojure.string :as string]))

(def ->kebab-case
  (m/lru csk/->kebab-case {} :lru/threshold 512))

(defn- request-camelCase->kebab-case-keyword
  [s]
  (let [s (name s)]
    (keyword (string/replace s #"[A-Z]" #(str "-" (string/lower-case %))))))

(defn- request-kebab-case->camelCaseKeyword
  [s]
  (let [s (name s)]
    (keyword (string/replace s #"-([a-z])" #(string/upper-case (second %))))))

(def camelCase->kebab-case-keyword
  (m/lru request-camelCase->kebab-case-keyword {} :lru/threshold 512))

(def kebab-case->camelCaseKeyword
  (m/lru request-kebab-case->camelCaseKeyword {} :lru/threshold 512))

(def ->kebab-case-keyword
  (m/lru csk/->kebab-case-keyword {} :lru/threshold 512))

(def ->kebab-case-string
  (m/lru csk/->kebab-case-string {} :lru/threshold 512))

(def ->PascalCaseString
  (m/lru csk/->PascalCaseString {} :lru/threshold 512))

(def ->PascalCaseKeyword
  (m/lru csk/->PascalCaseKeyword {} :lru/threshold 512))

(def ->PascalCase
  (m/lru csk/->PascalCase {} :lru/threshold 512))

(def ->snake_case
  (m/lru csk/->snake_case {} :lru/threshold 512))

(def ->snake_case_keyword
  (m/lru csk/->snake_case_keyword {} :lru/threshold 512))

(def ->camelCaseKeyword
  (m/lru csk/->camelCaseKeyword {} :lru/threshold 512))