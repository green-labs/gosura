# gosura.auth 





## `->auth-result`
``` clojure

(->auth-result auth ctx)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/auth.clj#L25-L31)</sub>
## `apply-fn-with-ctx-at-first`
``` clojure

(apply-fn-with-ctx-at-first auth ctx)
```


auth: auth 혹은 [fn & args]
  auth가 fn인 경우, (fn ctx)를 반환합니다
  auth가 [fn & args] 형태의 시퀀스인 경우, (apply fn ctx args)를 반환합니다. (thread-first와 유사)
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/auth.clj#L12-L23)</sub>
## `config-filter-opts`
``` clojure

(config-filter-opts filters ctx)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/auth.clj#L33-L38)</sub>
# gosura.builders.resolver 





## `build-resolvers`
``` clojure

(build-resolvers schema)
```


GraphQL 스키마 맵을 순회하며 :resolve 함수를 찾습니다.
   :resolve의 함수 Symbol을 활용해서 새로운 map을 :resolvers에 반환합니다.
   변경된 스키마 맵(:schema)과 새롭게 생성한 리졸버 맵(:resolvers)은
   lacinia의 attach-resolvers 함수에서 사용합니다.

   입력 : schema
   출력 : {:schema {...} :resolvers {...}}

   예)
   {:resolve farmmorning.user/get-address
    ...
    :resolve farmmorning.crop/get-name}
    ->
   {:schema    {:resolve :farmmorning.user/get-address
                :resolve :farmmorning.crop/get-name
    :resolvers {:farmmorning.user/get-address farmmorning.user/get-address
                :farmmorning.crop/get-name farmmorning.crop/get-name}}
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/builders/resolver.clj#L42-L64)</sub>
# gosura.core 


relay.dev spec 의 Connection, Node 를 구현하는
   lacinia schema, resolver-fn 을 생성합니다.

   gosura resolver-config edn 파일을 정의하면 생성합니다.
   gosura 에서 생성하는 resolver-fn 이 부적합 할 때는 따로 작성하는 것이 더 적절할 수 있습니다.

   주의) resolver-config edn 에 사용하는 ns 는 (ns 선언만 있는 빈 파일을 만들고) 그 네임스페이스를 사용하세요.



## `find-resolver-fn`
``` clojure

(find-resolver-fn resolver-key)
```


resolver-key 에 따라 적절한 resolver-fn 함수를 리턴한다.

   보통 ns `gosura.helpers.resolver` 의 resolver-fn 를 리턴한다. 
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/core.clj#L57-L77)</sub>
## `generate-all`
``` clojure

(generate-all resolver-configs)
```


GraphQL relay spec에 맞는 기본적인 resolver들을 여러개 동시에 생성한다.
   hash-map의 vector를 인자를 받으며 execute-one이 vector의 사이즈만큼 실행된다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/core.clj#L171-L177)</sub>
## `generate-one`
``` clojure

(generate-one resolver-config)
```


gosura resolver-config edn 을 받아
   :target-ns 에 resolver-fn 을 생성(intern)합니다.

   gosura `resolver-config` edn 예)
   ```
   {:target-ns         ns
    :resolvers         {:resolve-connection               {:node-type             node-type
                                                           :db-key                db-key
                                                           :table-fetcher         superfetcher
                                                           :pre-process-arguments pre-process-arguments
                                                           :post-process-row      post-process-row}
                        :resolve-by-fk                    {:settings {:auth my-auth-fn}
                                                           :node-type         node-type
                                                           :superfetcher      superfetcher/->Fetch
                                                           :post-process-row  post-process-row
                                                           :fk-in-parent      :fk-in-parent}
                        :resolve-connection-by-example-id {:settings {:auth [my-auth-fn-2 #{my-role}]}
                                                           :node-type         node-type
                                                           :superfetcher      superfetcher/->FetchByExampleId
                                                           :post-process-row  post-process-row}}}
   ```
   
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/core.clj#L79-L169)</sub>
# gosura.edn 





## `read-config`
``` clojure

(read-config path)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/edn.clj#L8-L10)</sub>
# gosura.helpers.db 





## `add-page-options`
``` clojure

(add-page-options sql {:keys [order-direction order-by limit offset]} table-name-alias)
(add-page-options sql page-options)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L138-L149)</sub>
## `batch-args-filter-pred`
``` clojure

(batch-args-filter-pred batch-args)
(batch-args-filter-pred batch-args table-name-alias)
```


WHERE (key1, key2) IN ((value1-1, value1-2), (value2-1, value2-2) ...)
   꼴의 HoneySQL 식을 반환합니다.

   ## 입력과 출력
     * 입력: key가 모두 같고 value만 다른 map의 모음을 받습니다.
            예) [{:country-code "JP", :id "1204"}
                {:country-code "JP", :id "1205"}
                {:country-code "KR", :id "1206"}]
   
     * 출력: [:in (:composite :country-code :id)
                 ((:composite "JP" "1204") (:composite "JP" "1205") (:composite "KR" "1206"))]
   
   ## HoneySQL을 통해 SQL 문으로 변환한 결과
     => ["WHERE (country_code, id) IN ((?, ?), (?, ?), (?, ?))" "JP" "1204" "JP" "1205" "KR" "1206"]
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L151-L174)</sub>
## `col-with-table-name`
``` clojure

(col-with-table-name table-name col-name)
```


column에 table 이름을 명시한다
   input: table-name :g, col-name :col1
   output: :g.col1
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L15-L27)</sub>
## `col-with-table-name?`
``` clojure

(col-with-table-name? col-name)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L11-L13)</sub>
## `cursor-filter-pred`
``` clojure

(cursor-filter-pred
 {:as _page-options, :keys [order-by order-direction cursor-id cursor-ordered-value]}
 table-name-alias)
(cursor-filter-pred page-options)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L29-L42)</sub>
## `delete!`
``` clojure

(delete! db table-name where-params)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L252-L256)</sub>
## `execute!`
``` clojure

(execute! ds qs)
(execute! ds qs opts)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L194-L198)</sub>
## `execute-one!`
``` clojure

(execute-one! ds qs)
(execute-one! ds qs opts)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L200-L204)</sub>
## `fetch!`
``` clojure

(fetch! ds qs)
(fetch! ds qs opts)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L182-L186)</sub>
## `fetch-one!`
``` clojure

(fetch-one! ds qs)
(fetch-one! ds qs opts)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L188-L192)</sub>
## `honey-sql-format-options`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L178-L180)</sub>
## `insert`
``` clojure

(insert db table-name cols data)
(insert db table-name cols data opts)
```


db
   table-name: a table name; keyword; ex. :table-name
   data: [{:col1 1 :col2 2 :col3 3}, {:col1 4 :col2 5 :col3 6}, ...]
   cols: a vector of keywords. ex) [:col1 :col2]
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L229-L242)</sub>
## `insert-one`
``` clojure

(insert-one db table-name cols data)
(insert-one db table-name cols data opts)
```


db
   table-name: a table name; keyword; ex. :table-name
   data: {:col1 1 :col2 2 :col3 3}
   cols: a vector of keywords. ex) [:col1 :col2]
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L214-L227)</sub>
## `join-for-filter-options`
``` clojure

(join-for-filter-options join-rules filter-options)
```


지정한 규칙과 필터링 옵션에 따라 조인 조건들을 선택한다.

  인자
  * join-rules - 조인 규칙들의 시퀀스. 각 규칙은 다음 값을 갖는 해시맵이다.
    * :join - 허니 SQL 조인 식
    * :for - 조인 조건을 filter-options에서 검사할 키들의 집합
  * filter-options - WHERE 식으로 변환하기 전의 DB 조회 조건들의 해시맵

  반환 - 선택된 허니 SQL 조인 식들을 모은 벡터

  예
  (join-for-filter-options
    [{:join [:users [:= :transactions.buyer-id :users.id]]
      :for  #{:buyer-name :buyer-address :buyer-phone-number}}
     {:join [:products [:= :transactions.product-id :products.id]]
      :for  #{:product-name}}]
    {:buyer-name "박연오"})
  => [:users [:= :transactions.buyer-id :users.id]]
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L106-L136)</sub>
## `order-by-page-options`
``` clojure

(order-by-page-options {:keys [order-by order-direction]})
```


정렬 기준 값(order-by)과 ID를 정렬 방향(order-direction)에 맞춰 정렬하도록 하는 DSL을 만든다.
   * 인자
     * page-options
       * :order-by        정렬 기준 값 (기본값 :id)
       * :order-direction 정렬 방향 (기본값 :asc)
   * 반환 - HoneySQL DSL의 ORDER BY 절

   * 함수 작성시 주의점
     order-by 와 :id 의 정렬 방향이 일치해야 한다.
     where 절에서 (cursor.order_by, cursor.id) < (row.order_by, row.id) 으로 두 값을 묶어 자르기 때문이다.

     <잘못된 예> 두 정렬값을 서로 다른 방향으로 설정하는 경우
     ORDER BY value ASC, ID DESC
     [1] 1000, 1
     [2] 1001, 4  ; 페이지 2 커서
     [3] 1001, 3
     [4] 1001, 2
     [5] 1002, 5

     페이지 1
     WHERE (-Inf, -Inf) < (row.value, row.id)  ; => [1], [2], [3], [4], [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [1], [2]

     페이지 2
     WHERE (1001, 4) < (row.value, row.id)  ; => [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [5]  ([3], [4] 누락)

     <올바른 예>
     ORDER BY value ASC, ID DESC
     [1] 1000, 1
     [2] 1001, 2  ; 페이지 2 커서
     [3] 1001, 3
     [4] 1001, 4
     [5] 1002, 5

     페이지 1
     WHERE (-Inf, -Inf) < (row.value, row.id)  ; => [1], [2], [3], [4], [5]
     ORDER BY value ASC, id ASC LIMIT 2  ; => [1], [2]

     페이지 2
     WHERE (1001, 2) < (row.value, row.id)  ; => [3], [4], [5]
     ORDER BY value ASC, id DESC LIMIT 2  ; => [3], [4]
   
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L44-L90)</sub>
## `query-timeout`

waiting to complete query state, unit time: seconds
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L176-L176)</sub>
## `remove-empty-options`
``` clojure

(remove-empty-options options)
```


인자
  * options - WHERE 식으로 변환하기 전의 DB 조회 조건들의 해시맵

  예)
  (remove-empty-options {:a 1, :b [1 2 3], :c [], :d {}, :e nil, :f #{1 2}})
  => {:a 1, :b [1 2 3], :f #{1 2}}
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L92-L104)</sub>
## `unqualified-kebab-fetch!`
``` clojure

(unqualified-kebab-fetch! ds qs)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L206-L208)</sub>
## `unqualified-kebab-fetch-one!`
``` clojure

(unqualified-kebab-fetch-one! ds qs)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L210-L212)</sub>
## `update!`
``` clojure

(update! db table-name data where-params)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/db.clj#L244-L250)</sub>
# gosura.helpers.error 





## `error`
``` clojure

(error resolver-errors)
(error resolved-value resolver-errors)
```


resolve-as를 사용하여 error를 반환하고 기본적으로 값으로는 nil을 반환하도록 한다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/error.clj#L4-L9)</sub>
# gosura.helpers.node 





## `tag-with-subtype`
``` clojure

(tag-with-subtype {:keys [subtype], :as row} subtype->node-type)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/node.clj#L5-L10)</sub>
# gosura.helpers.relay 





## `build-connection`
``` clojure

(build-connection order-by page-direction page-size cursor-id nodes)
```


order-by: 정렬 기준
   page-direction: forward or backward 페이지네이션 방향
   page-size: edges의 데이터 개수
   cursor-id: 현재 위치한 cursor의 id (db id)
   nodes: 노드의 시퀀스. 각 노드들 속에는, :db-id에 db의 row id가 들어 있고, :id에 릴레이 노드 id가 들어 있어야 함
          (즉, build-node에 의해 만들어진 노드 맵이어야 함)
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L134-L164)</sub>
## `build-filter-options`
``` clojure

(build-filter-options arguments additional-filter-opts)
```


relay connection 조회에 필요한 filter options를 빌드합니다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L166-L169)</sub>
## `build-node`
``` clojure

(build-node row node-type)
(build-node row node-type post-process-row)
```


db fetcher가 반환한 행 하나를 graphql node 형식에 맞도록 가공합니다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L119-L132)</sub>
## `build-page-options`
``` clojure

(build-page-options
 {:keys [first last after before order-by order-direction], :or {order-by :id, order-direction :ASC}, :as args})
```


relay connection 조회에 필요한 page options를 빌드합니다.
   (default) 10개의 데이터를 id기준으로 정방향으로 오름차순으로 가지고 옵니다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L193-L228)</sub>
## `decode-arguments`
``` clojure

(decode-arguments encoded-arguments)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L68-L69)</sub>
## `decode-cursor`
``` clojure

(decode-cursor cursor)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L49-L59)</sub>
## `decode-global-id->db-id`
``` clojure

(decode-global-id->db-id global-id)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L41-L42)</sub>
## `decode-id`
``` clojure

(decode-id id)
```


Relay 노드 ID를 node-type과 db-id의 맵으로 디코드합니다.
   "bm90aWNlLTI=" -> {:node-type "notice", :db-id "2"}
   주의: decode된 db id는 string임.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L26-L39)</sub>
## `encode-arguments`
``` clojure

(encode-arguments arguments)
```


arguments 인코드, 왜 쓰는가? 통계 쿼리에는 객체의 고유성이라는 게 없다.
  하지만 릴레이 클라이언트에서 이전에 요청한 질의에 따라 글로벌 ID를 이용한 갱신이 필요하다.
  그래서 객체 ID가 아닌 필터링 인자를 기준으로 글로벌 ID를 도출하여 사용하고 있다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L61-L66)</sub>
## `encode-cursor`
``` clojure

(encode-cursor {:keys [id ordered-values]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L44-L47)</sub>
## `encode-id`
``` clojure

(encode-id node-type db-id)
```


Node 레코드를 입력받아 Base64 문자열의 Relay 노드 Id로 인코딩합니다.
   (:notice, 2) -> "bm90aWNlLTI="
   
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L12-L17)</sub>
## `encode-node-id`
``` clojure

(encode-node-id {:keys [node-type db-id], :as node})
```


DB ID를 노드 ID로 변경한 맵을 반환합니다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L19-L24)</sub>
## `extend-relay-types`
``` clojure

(extend-relay-types schema)
```


lacinia schema에서 정의한 objects들에게
   Graphql relay spec에 맞는 edges, connections를 추가해줍니다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L94-L102)</sub>
## `node->cursor`
``` clojure

(node->cursor order-by node)
```


order-by: 정렬할 기준 컬럼. 타입: 키워드
   node: 커서로 변환할 노드
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L104-L112)</sub>
## `node->edge`
``` clojure

(node->edge order-by node)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L114-L117)</sub>
## `node-resolver`

Relay가 사용하는 node(id: ID!) 쿼리를 다형적으로 처리하기 위한 defmulti 입니다.
   Node 인터페이스를 구현하는 각 타입에서 defmethod를 구현하면 됩니다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/relay.clj#L71-L75)</sub>
# gosura.helpers.resolver 


resolver-fn 의 모음.

  resolve-connection
  resolve-connection-by-fk
  resolve-connection-by-pk-list
  resolve-by-fk
  resolve-by-parent-pk
  ...

  를 resolver-fn 이라 부르자, 약속해봅니다.
  



## `common-pre-process-arguments`
``` clojure

(common-pre-process-arguments arguments)
```


인자 맵에 일반적인 전처리를 한다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L149-L156)</sub>
## `decode-global-id-in-arguments`
``` clojure

(decode-global-id-in-arguments arguments)
```


인자 맵의 ID들(열이 id, 그리고 -id로 끝나는 것들)의 값을 글로벌 ID에서 ID로 디코드한다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L142-L147)</sub>
## `decode-global-ids-in-arguments`
``` clojure

(decode-global-ids-in-arguments arguments)
```


인자 맵의 ID들(열이 ids, 그리고 -ids로 끝나는 것들)의 값을 글로벌 ID에서 ID로 디코드한다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L135-L140)</sub>
## `decode-value-in-args`
``` clojure

(decode-value-in-args args suffix f)
```


suffix를 가진 key값들을 찾아서 value가 nil이 아닌 경우 value를 디코딩한다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L126-L133)</sub>
## `defnode`
``` clojure

(defnode node-type & fdecl)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L108-L122)</sub>
## `defresolver`
``` clojure

(defresolver name & fdecl)
```


Macro.


lacinia 용 resolver 함수를 만듭니다.

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
  :node-type - relay resolver 일때 설정하면, edge/node와 :pageInfo의 start/endCursor 처리를 같이 해줍니다.
  :return-camel-case? - 반환값을 camelCase 로 변환할지 설정합니다. (기본값 true)
  :required-keys-in-parent - 부모(hash-map)로부터 필요한 required keys를 설정합니다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L81-L106)</sub>
## `keys-not-found`
``` clojure

(keys-not-found parent required-keys-in-parent)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L30-L34)</sub>
## `pack-mutation-result`
``` clojure

(pack-mutation-result db db-fetcher filter-options {:keys [node-type post-process-row]})
```


Lacinia 변환 리졸버 응답용 변환 내역을 꾸며 반환한다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L353-L358)</sub>
## `parse-fdecl`
``` clojure

(parse-fdecl fdecl)
```


함수의 이름 뒤에 오는 선언부를 파싱합니다. doc-string와 option이 있을 수도 있고, 없을 수도 있기 때문에 그를 적절히 파싱해 줍니다.
  (fdecl이라는 이름은 core의 defn 구현에서 쓰이는 이름을 따왔습니다)
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L65-L79)</sub>
## `resolve-by-fk`
``` clojure

(resolve-by-fk
 context
 _arguments
 parent
 {:keys [db-key node-type superfetcher post-process-row fk-in-parent additional-filter-opts]})
```


Lacinia 리졸버로서 config 설정에 따라 단건 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :db-key            사용할 DB 이름
    * :superfetcher      슈퍼페처
    * :post-process-row  결과 객체 목록 후처리 함수 (예: identity)
    * :fk-in-parent      부모 엔티티로부터 이 엔티티의 ID를 가리키는 FK 칼럼 이름 (예: :gl-crop-id)

  ## 반환
  * 객체 하나
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L201-L232)</sub>
## `resolve-by-parent-pk`
``` clojure

(resolve-by-parent-pk
 context
 _arguments
 parent
 {:keys [db-key node-type superfetcher post-process-row additional-filter-opts]})
```


parent 객체의 primary key (보통 id)와 child의 foreign key 기반으로 child 하나를 resolve한다.
  parent:child가 1:0..1일 것을 가정함. DB 제약으로 child가 n개 붙는 것을 막을 수는 없지만,
  GraphQL에서 Parent->Optional<Child> 관계를 지원하기 위한 용도임
  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력 (지원 안함)
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :db-key           사용할 DB 이름
    * :superfetcher     슈퍼페처
    * :post-process-row 결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L169-L199)</sub>
## `resolve-connection`
``` clojure

(resolve-connection
 context
 arguments
 _parent
 {:keys [node-type db-key table-fetcher pre-process-arguments post-process-row additional-filter-opts]})
```


Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :node-type
    * :db-key                조회할 데이터베이스 이름 (예: :db, :farmmorning-db)
    * :table-fetcher         테이블 조회 함수 (예: bulk-sale/fetch-crops)
    * :pre-process-arguments
    * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)
    * :additional-filter-options 추가로 필요한 필터 옵션을 제공 받는다 예) jwt 토큰이 담고 있는 id가 가리키는 실제적 이름 등 (예: user-id(대부분), buyer-id)

  ## 반환
  * Connection
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L234-L272)</sub>
## `resolve-connection-by-fk`
``` clojure

(resolve-connection-by-fk
 context
 arguments
 parent
 {:keys [db-key node-type superfetcher post-process-row additional-filter-opts]})
```


Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
    * :superfetcher          슈퍼페처
    * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L313-L350)</sub>
## `resolve-connection-by-pk-list`
``` clojure

(resolve-connection-by-pk-list
 context
 arguments
 parent
 {:keys [db-key node-type table-fetcher pk-list-name post-process-row pk-list-name-in-parent additional-filter-opts]})
```


Primary Key를 기준으로 데이터를 조회하며 Connection Spec에 맡게 반환한다
   Lacinia 리졸버로서 config 설정에 따라 목록 조회 쿼리를 처리한다.

  ## 인자
  * context   리졸버 실행 문맥
  * arguments 쿼리 입력
  * parent    부모 노드
  * config    리졸버 동작 설정
   * :pk-list-name pk-list로 데이터를 조회할 때 이름
   * :pk-list-name-in-parent pk-list로 데이터를 조회할 때 부모로부터 전달 받는 이름
   * :post-process-row     결과 객체 목록 후처리 함수 (예: identity)

  ## 반환
  * 객체 목록
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L275-L311)</sub>
## `resolve-create-one`
``` clojure

(resolve-create-one
 ctx
 args
 _parent
 {:keys
  [node-type
   db-key
   table-fetcher
   mutation-fn
   mutation-tag
   additional-filter-opts
   pre-process-arguments
   post-process-row]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L360-L386)</sub>
## `resolve-delete-one`
``` clojure

(resolve-delete-one ctx args _parent {:keys [db-key mutation-fn additional-filter-opts]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L417-L430)</sub>
## `resolve-one`
``` clojure

(resolve-one
 context
 arguments
 _parent
 {:keys [node-type db-key fetch-one pre-process-arguments post-process-row additional-filter-opts]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L458-L473)</sub>
## `resolve-update-multi`
``` clojure

(resolve-update-multi
 ctx
 args
 _parent
 {:keys
  [node-type
   db-key
   table-fetcher
   mutation-fn
   mutation-tag
   additional-filter-opts
   pre-process-arguments
   post-process-row]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L432-L456)</sub>
## `resolve-update-one`
``` clojure

(resolve-update-one
 ctx
 args
 _parent
 {:keys
  [node-type
   db-key
   table-fetcher
   mutation-fn
   mutation-tag
   additional-filter-opts
   pre-process-arguments
   post-process-row]})
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L388-L415)</sub>
## `wrap-resolver-body`
``` clojure

(wrap-resolver-body {:keys [this ctx arg parent]} option args body)
```


Macro.


GraphQL 리졸버가 공통으로 해야 할 auth 처리, case 변환 처리를 resolver body의 앞뒤에서 해 주도록 wrapping합니다.

  this, ctx, arg, parent: 상위 리졸버 생성 매크로에서 만든 심벌
  option: 리졸버 선언에 지정된 옵션 맵
  args: 리졸버 선언의 argument vector 부분
  body: 리졸버의 body expression 부분
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver.clj#L36-L63)</sub>
# gosura.helpers.resolver2 


gosura.helpers.resolver의 v2입니다.



## `defresolver`
``` clojure

(defresolver name & fdecl)
```


Macro.


lacinia 용 resolver 함수를 만듭니다.

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
  :filters - 특정 필터 로직을 넣습니다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver2.clj#L44-L69)</sub>
## `wrap-resolver-body`
``` clojure

(wrap-resolver-body {:keys [this ctx arg parent]} option args body)
```


Macro.


GraphQL 리졸버가 공통으로 해야 할 auth 처리, case 변환 처리를 resolver body의 앞뒤에서 해 주도록 wrapping합니다.

  this, ctx, arg, parent: 상위 리졸버 생성 매크로에서 만든 심벌
  option: 리졸버 선언에 지정된 옵션 맵
  args: 리졸버 선언의 argument vector 부분
  body: 리졸버의 body expression 부분
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/resolver2.clj#L13-L41)</sub>
# gosura.helpers.response 





## `->delete-response`
``` clojure

(->delete-response id)
```


mutation: delete 시의 response
   lacinia schema의 :DeleteSuccess object 를 따른다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L41-L45)</sub>
## `->mutation-response`
``` clojure

(->mutation-response node tag)
```


mutation: create/update (생성/수정) 시의 response
   lacinia schema의 :MutationPayload interface를 따른다
   mutation response인 {:result {:type :Node}}로 변환한다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L47-L52)</sub>
## `error-response`
``` clojure

(error-response error-code message)
```



   error-code
   - :INVALID_REQUEST
   - :NOT_EXIST
   - :NOT_ALLOWED
   - :NOT_AVAILABLE
   - :NOT_AUTHENTICATED
   - :UNKNOWN_ERROR
   
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L6-L18)</sub>
## `not-exist-error`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L20-L21)</sub>
## `server-error`
``` clojure

(server-error profile error)
(server-error profile error message)
```


profile: 환경
   가능한 profile: dev/staging/prod
   sentry 메시지도 함께 보낸다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L26-L39)</sub>
## `unknown-error`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/response.clj#L23-L24)</sub>
# gosura.helpers.superlifter 





## `->lacinia-promise`
``` clojure

(->lacinia-promise sl-result)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L10-L15)</sub>
## `superfetch`
``` clojure

(superfetch many env {:keys [db-key table-fetcher id-column filter-key]})
```



  ## 인자
  * id-column   superfetch 결과의 매핑 단위가 되는 (parent 하나에 대응되는) 컬럼 이름
  * filter-key  table-fetcher에서 in 조건을 걸어줄 컬럼 이름
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L21-L50)</sub>
## `superfetch-v2`
``` clojure

(superfetch-v2 many env {:keys [db-key table-fetcher id-in-parent]})
```


superfetcher로부터 N개의 쿼리 전달받아 벌크로 fetch 합니다.
   fetch 할 때는 인자로 받은 table-fetcher 함수를 이용합니다.

   table-fetcher에는 `filter-options` 가 전달되는데, 
   해당 맵 안에 `batch-args` 에는 가공되지 않은 전체 argument가 들어있습니다.
   
   ## 인자
     * many - id와 argument를 가진 superfetcher.Fetch 목록
              예: (#farmmorning.api_global.country_region.superfetcher.Fetch
                   {:id 1501533529, :arguments {:country-code "JP", :id "1204", :page-options nil}} ...)
     * env - {:db #object[com.zaxxer.hikari.HikariDataSource] ...}
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L66-L97)</sub>
## `superfetcher`
``` clojure

(superfetcher name params)
```


Macro.


superfetcher 기본 form을 쉽게 제공한다
   defrecord를 생성하므로 name은 PascalCase로 작성하는 걸 원칙으로 한다
   호출할 때에는 ->name 으로 사용한다
   생성예시) (superfetcher FetchByExampleId {...})
   사용예시) ->FetchByExampleId
   
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L52-L64)</sub>
## `superfetcher-v2`
``` clojure

(superfetcher-v2 name params)
```


Macro.


superfetcher 기본 form을 쉽게 제공한다
   defrecord를 생성하므로 name은 PascalCase로 작성하는 걸 원칙으로 한다
   호출할 때에는 ->name 으로 사용한다
   생성예시) (superfetcher FetchByExampleId {...})
   사용예시) ->FetchByExampleId
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L99-L110)</sub>
## `with-superlifter`
``` clojure

(with-superlifter ctx body)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/helpers/superlifter.clj#L17-L19)</sub>
# gosura.schema 





## `base-mutation-config-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L10-L19)</sub>
## `base-root-query-multi-config-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L21-L28)</sub>
## `base-root-query-one-config-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L30-L37)</sub>
## `decoded-id-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L71-L74)</sub>
## `node-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L66-L69)</sub>
## `resolver-settings-schema`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L3-L7)</sub>
## `resolvers-map-schema`

resolvers 설정에 필요한 스키마 정의
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/schema.clj#L39-L64)</sub>
# gosura.util 





## `keyword-vals->string-vals`
``` clojure

(keyword-vals->string-vals hash-map)
```


hash-map value 값에 keyword가 있으면 String으로 변환해준다
   
   사용예시) enum 값 때문에 keyword가 들어올 일이 있음
   한계: 1 depth 까지만 적용
   
   TODO) 조금 더 발전 시켜서 defresolver나 resolve-xxx 에서 무조건 이 로직을 타도록 하는 것도 좋을 듯
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L9-L21)</sub>
## `qualified-symbol->requiring-var!`
<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L65-L69)</sub>
## `requiring-var!`
``` clojure

(requiring-var! sym|var)
```


qualified-symbol을 var로 변환합니다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L71-L77)</sub>
## `send-sentry-server-event`
``` clojure

(send-sentry-server-event event)
```

<sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L33-L36)</sub>
## `stringify-ids`
``` clojure

(stringify-ids row)
```


행의 ID들(열이 id, 그리고 -id로 끝나는 것들)을 문자열로 변경한다.
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L56-L63)</sub>
## `transform-keys->camelCaseKeyword`
``` clojure

(transform-keys->camelCaseKeyword form)
```


재귀적으로 form 안에 포함된 모든 key를 camelCase keyword로 변환한다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L28-L31)</sub>
## `transform-keys->kebab-case-keyword`
``` clojure

(transform-keys->kebab-case-keyword form)
```


재귀적으로 form 안에 포함된 모든 key를 camelCase keyword로 변환한다
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L23-L26)</sub>
## `update-resolver-result`
``` clojure

(update-resolver-result resolver-result update-fn)
```


lacinia resolver의 리턴 값에서, 에러를 제외한 데이터 부분에 대해서 함수를 적용시켜 업데이트해 줍니다.
  `resolver-result`의 타입은 순수한 map일수도, WrappedValue일수도, ResolverResultImpl일 수도 있습니다.
  각 타입별로 resolver result 안에 데이터가 들어가 있는 위치가 다르므로, 그에 맞게 적절한 위치를 찾아서 update-fn 함수를 적용해 줍니다.
  
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L38-L47)</sub>
## `update-vals-having-keys`
``` clojure

(update-vals-having-keys m ks f)
```


m: map(데이터)
   ks: 특정 키값
   f: val 업데이트 함수
<br><sub>[source](https://github.com/green-labs/gosura/blob/master/src/gosura/util.clj#L49-L54)</sub>
