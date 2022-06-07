(ns gosura.helpers.relay
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string]
            [gosura.schema :as gosura-schema]
            [malli.core :as m]
            [ring.util.codec :refer [base64-decode base64-encode]]
            [taoensso.nippy :as nippy])
  (:import (clojure.lang ExceptionInfo)
           java.util.Base64))

(defn ->node [node-type db-id]
  {:node-type (keyword node-type)
   :db-id (str db-id)})

(defn encode-id
  "Node 레코드를 입력받아 Base64 문자열의 Relay 노드 Id로 인코딩합니다.
   {:node-type \"notice\", :db-id 2} -> \"bm90aWNlLTI=\"
   "
  [{:keys [node-type db-id] :as node}]
  (when-not (m/validate gosura-schema/node-schema node)
    (throw (ex-info "Invalid node schema" node)))
  (.encodeToString (Base64/getEncoder) (.getBytes (str (name node-type) ":" db-id))))

(defn decode-id
  "Relay 노드 ID를 Node 레코드로 디코드합니다.
   \"bm90aWNlLTI=\" -> {:node-type \"notice\", :db-id 2}
   "
  [id]
  (when-not (string? id)
    (throw (ex-info "node id must be string" {:invalid-id id})))
  (let [[_ node-type db-id] (re-matches #"^(.*):(.*)$"
                                        (String. (.decode (Base64/getDecoder) id)))
        decoded-result (->node node-type db-id)]
    (when-not (m/validate gosura-schema/node-schema decoded-result)
      (throw (ex-info "Invalid node id" {:decoded-id decoded-result})))
    decoded-result))

(defn encode-global-id [node-type id]
  (encode-id {:node-type node-type, :db-id id}))

(defn decode-global-id->db-id [global-id]
  (-> global-id decode-id :db-id))

(defn encode-cursor [{:keys [id ordered-values]}]
  (-> [id (vec ordered-values)]
      nippy/freeze
      (#(.encodeToString (Base64/getEncoder) %))))

(defn decode-cursor [cursor]
  (try
    (let [[id ordered-values]
          (-> cursor
              (#(.decode (Base64/getDecoder) %))
              nippy/thaw)]
      {:id id, :ordered-values ordered-values})
    (catch ExceptionInfo _
      (throw (ex-info (str "Cursor cannot be decoded: " cursor)
                      {:side :client
                       :caused-by {:cursor cursor}})))))

(defn encode-arguments
  "arguments 인코드, 왜 쓰는가? 통계 쿼리에는 객체의 고유성이라는 게 없다.
  하지만 릴레이 클라이언트에서 이전에 요청한 질의에 따라 글로벌 ID를 이용한 갱신이 필요하다.
  그래서 객체 ID가 아닌 필터링 인자를 기준으로 글로벌 ID를 도출하여 사용하고 있다."
  [arguments]
  (-> arguments nippy/freeze base64-encode))

(defn decode-arguments [encoded-arguments]
  (-> encoded-arguments base64-decode nippy/thaw))

(defn encode-node-id
  "DB ID를 노드 ID로 변경한 맵을 반환합니다."
  [node-type m]
  (when-not (map? m)
    (throw (ex-info "data to convert must be map" {:invalid-data m})))
  (update m :id #(encode-id (->node node-type %))))

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
  [order-by node]
  (encode-cursor {:id             (:id node)
                  :ordered-values [(get node (csk/->camelCaseKeyword order-by))]}))  ; TODO: change trans-case time

(defn node->edge
  [order-by node]
  {:cursor (node->cursor order-by node)
   :node   node})

(defn build-connection
  "order-by: 정렬 기준
   page-direction: forward or backward 페이지네이션 방향
   limit: edges의 데이터 개수
   cursor-id: 현재 위치한 cursor의 id
   rows: 데이터"
  [node-type order-by page-direction limit cursor-id rows]
  (let [cursor-row? #(= (str (:id %)) (str cursor-id))
        prev-rows (when cursor-id
                    (->> (reverse rows)
                         (drop-while (complement cursor-row?))))
        has-prev? (boolean (seq prev-rows))
        remaining-rows (if cursor-id
                         (->> rows
                              (drop-while (complement cursor-row?))  ; 커서 이전 행들을 제거
                              (drop 1))  ; 커서 행을 제거
                         rows)
        next-rows (drop limit remaining-rows)
        has-next? (boolean (seq next-rows))
        paged-rows (cond->> remaining-rows
                     limit (take limit)
                     (= page-direction :backward) reverse)
        edges (->> paged-rows
                   (map #(node->edge order-by %))
                   (map #(update-in % [:node :id] (fn [x] ; node > nodeType이 명시되어있으면 해당 nodeType을 사용하도록 한다
                                                    (encode-global-id (get-in % [:node :nodeType] node-type) x)))))]
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

(defn build-page-options
  "relay connection 조회에 필요한 page options를 빌드합니다.
   (default) 10개의 데이터를 id기준으로 정방향으로 오름차순으로 가지고 옵니다"
  [{:keys [first last
           after before
           order-by order-direction]
    :or {order-by        :id
         order-direction :ASC} :as args}]
  (validate-connection-arguments args)
  (let [page-direction (cond first :forward
                             last :backward
                             :else :forward)
        limit (or (case page-direction
                    :forward first
                    :backward last) 10)
        cursor (when-let [encoded-cursor (case page-direction
                                           :forward after
                                           :backward before)]
                 (decode-cursor encoded-cursor))
        cursor-ordered-values (:ordered-values cursor)
        cursor-id (:id cursor)
        order-by (csk/->kebab-case-keyword order-by)
        order-direction (csk/->kebab-case-keyword order-direction)  ; ASC/DESC -> asc/desc
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
     :limit                (+ limit 2)}))
