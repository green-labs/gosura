(ns gosura.helpers.resolver2
  "gosura.helpers.resolver의 v2입니다."
  (:require [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [failjure.core :as f]
            [gosura.auth :as auth]
            [gosura.helpers.error :as error]
            [gosura.helpers.resolver :refer [parse-fdecl
                                             keys-not-found]]
            [gosura.util :as util :refer [transform-keys->camelCaseKeyword
                                          transform-keys->kebab-case-keyword
                                          update-resolver-result]]))

(defmacro wrap-resolver-body
  "GraphQL 리졸버가 공통으로 해야 할 auth 처리, case 변환 처리를 resolver body의 앞뒤에서 해 주도록 wrapping합니다.

  this, ctx, arg, parent: 상위 리졸버 생성 매크로에서 만든 심벌
  option: 리졸버 선언에 지정된 옵션 맵
  args: 리졸버 선언의 argument vector 부분
  body: 리졸버의 body expression 부분"
  [{:keys [this ctx arg parent]} option args body]
  (let [{:keys [auth kebab-case? return-camel-case? required-keys-in-parent filters]
         :or   {kebab-case?             true
                return-camel-case?      true
                required-keys-in-parent []}} option
        result                                                                                                                                                     (gensym 'result_)
        auth-filter-opts                                                                                                                                           `(auth/->auth-result ~auth ~ctx)
        config-filter-opts                                                                                                                                         `(auth/config-filter-opts ~filters ~ctx)
        arg                                                                                                                                                        `(merge ~arg ~auth-filter-opts ~config-filter-opts)
        arg'                                                                                                                                                       (if kebab-case? `(transform-keys->kebab-case-keyword ~arg) arg)
        parent'                                                                                                                                                    (if kebab-case? `(transform-keys->kebab-case-keyword ~parent) parent)
        keys-not-found                                                                                                                                             `(keys-not-found ~parent' ~required-keys-in-parent)
        params                                                                                                                                                     (if (nil? this) [ctx arg' parent'] [this ctx arg' parent'])
        let-mapping                                                                                                                                                (vec (interleave args params))]
    `(if (seq ~keys-not-found)
       (error/error {:message (format "%s keys are needed in parent" ~keys-not-found)})
       (if (and ~auth-filter-opts
                (not (f/failed? ~auth-filter-opts)))
         (let [~result (do (let ~let-mapping ~@body))]
           (cond-> ~result
             ~return-camel-case? (update-resolver-result transform-keys->camelCaseKeyword)))
         (resolve-as nil {:message "Unauthorized"})))))


(defmacro defresolver
  "lacinia 용 resolver 함수를 만듭니다.

  입력
  name - 함수 이름
  doc-string? - 문서
  option? - 설정
  args - 매개변수 [ctx arg parent]
  body - 함수 바디

  TODO: defn과 같이 attr-map을 받는 기능 추가

  가능한 설정
  :auth - 인증함수를 넣습니다. gosura.auth의 설명을 참고해주세요.
  :kebab-case? - arg/parent 의 key를 kebab-case로 변환할지 설정합니다. (기본값 true)
  :return-camel-case? - 반환값을 camelCase 로 변환할지 설정합니다. (기본값 true)
  :required-keys-in-parent - 부모(hash-map)로부터 필요한 required keys를 설정합니다.
  :filters - 특정 필터 로직을 넣습니다"
  {:arglists '([name doc-string? option? args & body])}
  [name & fdecl]
  (let [{:keys [doc-string option args body]} (parse-fdecl fdecl)
        header                                (if doc-string [name doc-string] [name])]
    `(defn ~@header [ctx# arg# parent#]
       (wrap-resolver-body {:ctx    ctx#
                            :arg    arg#
                            :parent parent#} ~option ~args ~body))))
