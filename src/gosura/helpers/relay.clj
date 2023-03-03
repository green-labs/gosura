(ns gosura.helpers.relay
  (:require [clojure.string]
            [gosura.case-format :as cf]
            [gosura.schema :as gosura-schema]
            [camel-snake-kebab.core :as csk]
            [gosura.util :refer [stringify-ids]]
            [malli.core :as m]
            [ring.util.codec :refer [base64-decode base64-encode]]
            [taoensso.nippy :as nippy])
  (:import (clojure.lang ExceptionInfo)
           java.util.Base64))

(defn encode-id
  "Node 레코드를 입력받아 Base64 문자열의 Relay 노드 Id로 인코딩합니다.
   (:notice, 2) -> \"bm90aWNlLTI=\"
   "
  [node-type db-id]
  (.encodeToString (Base64/getEncoder) (.getBytes (str (name node-type) ":" db-id))))

(defn encode-node-id
  "DB ID를 노드 ID로 변경한 맵을 반환합니다."
  [{:keys [node-type db-id] :as node}]
  (when-not (m/validate gosura-schema/node-schema node)
    (throw (ex-info "Invalid node schema" {:node node})))
  (assoc node :id (encode-id node-type db-id)))

(defn decode-id
  "Relay 노드 ID를 node-type과 db-id의 맵으로 디코드합니다.
   \"bm90aWNlLTI=\" -> {:node-type \"notice\", :db-id \"2\"}
   주의: decode된 db id는 string임."
  [id]
  (when-not (string? id) nil)
  (let [decoded (try (String. (.decode (Base64/getDecoder) id))
                     (catch IllegalArgumentException _ ""))
        [_ node-type db-id] (re-matches #"^(.*):(.*)$" decoded)
        decoded-result {:node-type (keyword node-type)
                        :db-id db-id}]
    (when (m/validate gosura-schema/decoded-id-schema decoded-result)
      decoded-result)))

(defn decode-global-id->db-id [global-id]
  (some-> global-id decode-id :db-id))

(defn encode-cursor [{:keys [id ordered-values]}]
  (-> [id (vec ordered-values)]
      nippy/freeze
      (#(.encodeToString (Base64/getEncoder) %))))

(defn decode-cursor [cursor]
  (try
    (let [decoded-bytes (base64-decode cursor)]
      (try
        ;; nippy/thaw 로 디코드되는 경우 식별자 기반 커서로 해석
        (let [id-based-cursor     (nippy/thaw decoded-bytes)
              [id ordered-values] id-based-cursor]
          {:id id, :ordered-values ordered-values})
        (catch ExceptionInfo _exception-info
          ;; String. 으로 디코드되는 경우 오프셋 기반 커서로 해석
          (let [offset-based-cursor   (String. decoded-bytes)
                [_placeholder offset] (clojure.string/split offset-based-cursor #":")]
            {:offset (parse-long offset)}))))
    (catch Exception _exception
      (throw (ex-info (str "Cursor cannot be decoded: " cursor)
                      {:side      :client
                       :caused-by {:cursor cursor}})))))

(defn encode-arguments
  "arguments 인코드, 왜 쓰는가? 통계 쿼리에는 객체의 고유성이라는 게 없다.
  하지만 릴레이 클라이언트에서 이전에 요청한 질의에 따라 글로벌 ID를 이용한 갱신이 필요하다.
  그래서 객체 ID가 아닌 필터링 인자를 기준으로 글로벌 ID를 도출하여 사용하고 있다."
  [arguments]
  (-> arguments nippy/freeze base64-encode))

(defn decode-arguments [encoded-arguments]
  (-> encoded-arguments base64-decode nippy/thaw))

(defmulti node-resolver
  "Relay가 사용하는 node(id: ID!) 쿼리를 다형적으로 처리하기 위한 defmulti 입니다.
   Node 인터페이스를 구현하는 각 타입에서 defmethod를 구현하면 됩니다."
  (fn [this _ctx _args _parent]
    (keyword (:node-type this))))

(defn- make-edge-connection-types
  ":Node 를 implements 하는 type인 경우 Edge, Connection 이 붙은 type 정보를 반환합니다."
  [k v]
  (when (contains? (set (:implements v)) :Node)
    (let [type-name (name k)
          edge-name (keyword (str type-name "Edge"))
          connection-name (keyword (str type-name "Connection"))]
      [edge-name
       {:implements [:Edge]
        :fields {:cursor {:type '(non-null String)}
                 :node   {:type `(~'non-null ~k)}}}
       connection-name
       {:implements [:Connection]
        :fields {:edges    {:type `(~'non-null (~'list (~'non-null ~edge-name)))}
                 :pageInfo {:type '(non-null :PageInfo)}
                 :count    {:type '(non-null Int)}}}])))

(defn extend-relay-types
  "lacinia schema에서 정의한 objects들에게
   Graphql relay spec에 맞는 edges, connections를 추가해줍니다."
  [schema]
  (update schema :objects
          #(reduce-kv (fn [m k v]
                        (apply assoc m
                               (concat [k v] (make-edge-connection-types k v))))
                      {} %)))

(defn node->cursor
  "order-by: 정렬할 기준 컬럼. 타입: 키워드
   node: 커서로 변환할 노드"
  [order-by node]
  ; 이 시점에서 node의 :id에는 인코드된 릴레이 노드 id가 들어 있기 때문에
  ; order-by가 :id로 들어오면 :db-id로 우회시켜 줘야 함
  (let [order-by (if (= order-by :id) :db-id order-by)]
    (encode-cursor {:id             (:db-id node)
                    :ordered-values [(get node order-by)]})))

(defn node->edge
  [order-by node]
  {:cursor (node->cursor order-by node)
   :node   node})

(defn build-node
  "db fetcher가 반환한 행 하나를 graphql node 형식에 맞도록 가공합니다"
  ([row node-type]
   (build-node row node-type identity))
  ([row node-type post-process-row]
   (when row
     (-> row
         (assoc :node-type node-type)
         (assoc :db-id (:id row)) ; id 인코드 후에도 db id를 사용해야 할 때가 있으므로, :db-id라는 키로 별도로 저장
         ; Note: 다형적인 node의 경우, node-type이 post-process-row에 의해 동적으로 결정될 수 있기 때문에,
         ; post-process-row가 (assoc :node-type)보다 뒤에 있어야 함.
         post-process-row
         encode-node-id
         stringify-ids))))

(defn build-connection
  "order-by: 정렬 기준
   page-direction: forward or backward 페이지네이션 방향
   page-size: edges의 데이터 개수
   cursor-id: 현재 위치한 cursor의 id (db id)
   nodes: 노드의 시퀀스. 각 노드들 속에는, :db-id에 db의 row id가 들어 있고, :id에 릴레이 노드 id가 들어 있어야 함
          (즉, build-node에 의해 만들어진 노드 맵이어야 함)"
  [order-by page-direction page-size cursor-id nodes]
  (let [cursor-row? #(= (str (:db-id %)) (str cursor-id))
        prev-rows (when cursor-id
                    (->> (reverse nodes)
                         (drop-while (complement cursor-row?))))
        has-prev? (boolean (seq prev-rows))
        remaining-rows (if cursor-id
                         (->> nodes
                              (drop-while (complement cursor-row?))  ; 커서 이전 행들을 제거
                              (drop 1))  ; 커서 행을 제거
                         nodes)
        next-rows (drop page-size remaining-rows)
        has-next? (boolean (seq next-rows))
        paged-rows (cond->> remaining-rows
                            page-size (take page-size)
                            (= page-direction :backward) reverse)
        edges (->> paged-rows
                   (map #(node->edge order-by %)))]
    {:count     (count edges)
     :page-info {:has-previous-page (case page-direction :forward has-prev? :backward has-next?)
                 :has-next-page     (case page-direction :forward has-next? :backward has-prev?)
                 :start-cursor      (or (->> edges first :cursor) "")
                 :end-cursor        (or (->> edges last :cursor) "")}
     :edges     edges}))

(defn build-filter-options
  "relay connection 조회에 필요한 filter options를 빌드합니다"
  [arguments additional-filter-opts]
  (merge arguments additional-filter-opts))

(defn- validate-connection-arguments [arguments]
  (when (and (:first arguments)
             (:last arguments))
    (throw (ex-info "first & last must not be input together."
                    {:side      :client
                     :caused-by {:arguments (select-keys arguments [:first :last])}})))
  (when (and (:first arguments)
             (:before arguments))
    (throw (ex-info "first & before must not be input together."
                    {:side      :client
                     :caused-by {:arguments (select-keys arguments [:first :before])}})))
  (when (and (:last arguments)
             (:after arguments))
    (throw (ex-info "last & after must not be input together."
                    {:side      :client
                     :caused-by {:arguments (select-keys arguments [:last :after])}})))
  (when (and (:after arguments)
             (:before arguments))
    (throw (ex-info "after & before must not be input together."
                    {:side      :client
                     :caused-by {:arguments (select-keys arguments [:after :before])}}))))

(def default-page-size 10)
(defn build-page-options
  "relay connection 조회에 필요한 page options를 빌드합니다.
   (default) 10개의 데이터를 id기준으로 정방향으로 오름차순으로 가지고 옵니다
   
   인자
   - :kebab-case? - order-by, order-direction를 kebab-case로 변환할지 설정합니다. (기본값 true)"
  [{:keys [first last
           after before
           order-by order-direction
           offset-based-pagination
           kebab-case?]
    :or {order-by        :id
         order-direction :ASC
         kebab-case?     true} :as args}]
  (validate-connection-arguments args)
  (let [page-direction (cond first :forward
                             last :backward
                             :else :forward)
        page-size (or (case page-direction
                        :forward first
                        :backward last)
                      default-page-size)
        limit (+ page-size 2)
        cursor (when-let [encoded-cursor (case page-direction
                                           :forward after
                                           :backward before)]
                 (decode-cursor encoded-cursor))
        cursor-ordered-values (when-not offset-based-pagination (:ordered-values cursor))
        cursor-id (when-not offset-based-pagination (:id cursor))
        offset (when offset-based-pagination (:offset cursor))
        order-by  (cond-> order-by kebab-case? csk/->kebab-case-keyword)
        order-direction (cf/UPPER_SNAKE_CASE_KEYWORD->kebab-case-keyword order-direction) ; ASC/DESC -> asc/desc
        order-direction (get #{:asc :desc} order-direction :asc)
        order-direction (case page-direction
                          :forward order-direction
                          :backward (get {:asc :desc
                                          :desc :asc}
                                         order-direction))]
    {:order-by             order-by
     :order-direction      order-direction
     :page-direction       page-direction
     :cursor-id            cursor-id
     :cursor-ordered-value (clojure.core/first cursor-ordered-values)
     :limit                limit
     :offset               offset
     :page-size            page-size}))

(defn decode-global-ids-by-keys
  "arguments 맵에서 ks의 키값 값을 재귀적으로 찾아 DB ID로 디코드합니다."
  [arguments ks]
  (let [ks (set ks)]
    (reduce-kv (fn [m k v]
                 (cond
                   (ks k) (assoc m k (if (coll? v)
                                       (map decode-global-id->db-id v)
                                       (decode-global-id->db-id v)))
                   (associative? v) (assoc m k (decode-global-ids-by-keys v ks))
                   :else (assoc m k v)))
               {}
               arguments)))

(comment
  (decode-cursor "TlBZAHFkAW4BZAE=")  ;; => {:id 1, :ordered-values [1]})
  (decode-cursor "b2Zmc2V0OjM=")      ;; => {:offset "3"}
  )
