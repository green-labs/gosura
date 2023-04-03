(ns gosura.helpers.case-format
  (:require [clojure.walk :as walk])
  (:import [clojure.lang Keyword]
           [com.google.common.base CaseFormat]))

(def camelCase ^CaseFormat CaseFormat/LOWER_CAMEL)
(def PascalCase ^CaseFormat CaseFormat/UPPER_CAMEL)
(def kebab-case ^CaseFormat CaseFormat/LOWER_HYPHEN)
(def snake_case ^CaseFormat CaseFormat/LOWER_UNDERSCORE)
(def UPPER_SNAKE_CASE ^CaseFormat CaseFormat/UPPER_UNDERSCORE)

(defn transform
  "문자열 s를 CaseFormat from 에서 CaseFormat to 형식의 문자열로 변환합니다.
   사용 가능한 CaseFormat: cf/camelCase, cf/PascalCase cf/kebab-case cf/snake_case cf/UPPER_SNAKE_CASE"
  ^String [^CaseFormat from ^CaseFormat to ^String s]
  (.to from to s))

(defn transform-keyword
  "키워드 k를 CaseFormat from 에서 CaseFormat to 형식의 문자열로 변환합니다.
   사용 가능한 CaseFormat: cf/camelCase, cf/PascalCase cf/kebab-case cf/snake_case cf/UPPER_SNAKE_CASE"
  ^Keyword [^CaseFormat from ^CaseFormat to ^Keyword k]
  (keyword (transform from to (name k))))

(defn camelCase->kebab-case ^String [^String s]
  (transform camelCase kebab-case s))

(defn camelCaseKeyword->kebab-case-keyword ^Keyword [^Keyword k]
  (keyword (camelCase->kebab-case (name k))))

(defn kebab-case->camelCase ^String [^String s]
  (transform kebab-case camelCase s))

(defn kebab-case-keyword->camelCaseKeyowrd ^Keyword [^Keyword k]
  (keyword (kebab-case->camelCase (name k))))

(defn snake_case->kebab-case ^String [^String s]
  (transform snake_case kebab-case s))

(defn snake_case_keyword->kebab-case-keyword ^Keyword [^Keyword k]
  (keyword (snake_case->kebab-case (name k))))

(defn kebab-case->snake_case ^String [^String s]
  (transform kebab-case snake_case s))

(defn kebab-case-keyword->snake_case_keyword ^Keyword [^Keyword k]
  (keyword (kebab-case->snake_case (name k))))

(defn kebab-case->PascalCase ^String [^String s]
  (transform kebab-case PascalCase s))

(defn kebab-case-keyword->PascalCaseKeyword ^Keyword [^Keyword k]
  (keyword (kebab-case->PascalCase (name k))))

(defn UPPER_SNAKE_CASE->kebab-case ^String [^String s]
  (transform UPPER_SNAKE_CASE kebab-case s))

(defn UPPER_SNAKE_CASE_KEYWORD->kebab-case-keyword ^Keyword [^Keyword k]
  (keyword (UPPER_SNAKE_CASE->kebab-case (name k))))

(defn transform-keys
  "재귀적으로 컬랙션의 모든 키를 변환합니다."
  [t coll]
  (letfn [(transform [[k v]] [(t k) v])]
    (walk/postwalk (fn [x] (if (map? x) (with-meta (into {} (map transform x)) (meta x)) x)) coll)))

(defn transform-keys-camelCase->kebab-case
  "재귀적으로 컬랙션의 모든 키를 camelCase->kebab-case 변환합니다."
  [m]
  (transform-keys camelCaseKeyword->kebab-case-keyword m))

(defn transform-keys-kebab-case->camelCase
  "재귀적으로 컬랙션의 모든 키를 kebab-case->camelCase 변환합니다."
  [m]
  (transform-keys kebab-case-keyword->camelCaseKeyowrd m))

(defn transform-keys-snake_case->kebab-case
  "재귀적으로 컬랙션의 모든 키를 snake_case->kebab-case 변환합니다."
  [m]
  (transform-keys snake_case_keyword->kebab-case-keyword m))

(defn transform-keys-kebab-case->snake_case
  "재귀적으로 컬랙션의 모든 키를 kebab-case->snake_case 변환합니다."
  [m]
  (transform-keys kebab-case-keyword->snake_case_keyword m))
