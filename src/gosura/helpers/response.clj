(ns gosura.helpers.response
  (:require [clojure.string :as string]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [gosura.helpers.relay :as relay]
            [gosura.util :as util]))

(defn error-response
  "
   error-code
   - :INVALID_REQUEST
   - :NOT_EXIST
   - :NOT_ALLOWED
   - :NOT_AVAILABLE
   - :NOT_AUTHENTICATED
   - :UNKNOWN_ERROR
   "
  [error-code message]
  (tag-with-type {:code    error-code
                  :message message} :Error))

(def not-exist-error
  (error-response :NOT_EXIST "데이터를 찾을 수 없습니다"))

(def unknown-error
  (error-response :UNKNOWN_ERROR "서버의 알 수 없는 에러"))

(defn server-error
  "profile: 환경
   가능한 profile: dev/staging/prod
   sentry 메시지도 함께 보낸다"
  ([profile error]
   (server-error profile error nil))
  ([profile error message]
   (let [message (if (= "prod" (string/lower-case profile))
                   "서버의 알 수 없는 에러"
                   (or message (ex-message error)))]
     (util/send-sentry-server-event {:message   message
                                     :name      (System/getenv "BRANCH_NAME")
                                     :throwable error})
     (error-response :UNKNOWN_ERROR message))))

(defn ->delete-response
  "mutation: delete 시의 response
   lacinia schema의 :DeleteSuccess object 를 따른다"
  [id]
  (tag-with-type {:deletedId id} :DeleteSuccess))

(defn ->mutation-response
  "mutation: create/update (생성/수정) 시의 response
   lacinia schema의 :MutationPayload interface 를 따른다
   mutation response인 {:result {:type :Node}}로 변환한다"
  [data node-type tag]
  (tag-with-type {:result (relay/encode-node-id node-type data)} tag))
