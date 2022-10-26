# Gosura
Data-driven GraphQL Resolvers on [lacinia](https://github.com/walmartlabs/lacinia) for Clojure.

# Motivation
다목적의 어플리케이션 개발을 하다보면 GraphQL을 이용하여 많고 다양한 query와 mutation을 만들어야합니다. 특히, 기본 query와 mutation을 작성하는 것은 중요하지만 반복적인 일이기도 합니다. 이 따분한 작업들을 조금 더 빠르고 쉽게 개발할 수 있도록 하는 여러가지 서비스들이 있는데 그 중 하나인 [hasura](https://hasura.io/)의 영향을 받아 [gosura](https://github.com/green-labs/gosura)가 만들어졌습니다.

기본적으로 GraphQL [relay](https://relay.dev/) 스펙을 이용한 어플리케이션 설계에 유용하도록 만들어져있습니다. 그리고 [EDN](https://github.com/edn-format/edn)을 이용하여 선언적으로 GraphQL query 및 mutation이 만들어지도록 하였습니다.

We are writing diverse and repetitive GraphQL queries and mutations where we are building general-purposed applications. When you follow [GraphQL](https://graphql.org/) and even [relay](https://relay.dev/), we should handle the relay specification with Clojure code. It's pretty annoying with us. So, we decided to make some helpers and [DSL](https://en.wikipedia.org/wiki/Domain-specific_language) for data-driven resolvers.

# Features
- An [EDN](https://github.com/edn-format/edn)-based GraphQL resolver language.
- Following GraphQL relay specifications.
- Generating query resolvers in a declarative way.
- Simple mapping 1:1, 1:N, N:M relations.
- Covering N+1 queries over [superlifter](https://github.com/oliyh/superlifter)

# Caveats
- currently only support mysql8.
- superlifter solves N+1 query but it doesn't take a optimal way in superlifter.
- not enough docstring and documentation.

# Installation
Use git dependency:
```clojure
green-labs/gosura {:git/url "https://github.com/green-labs/gosura"
                   :git/sha "f1d586669f37a3ca99e14739f9abfb1a02128274"}
```

# Core
This is almost full configs you're able to set.
```clojure
{:target-ns             animal.resolve ; a generated resolver's namespace
 :node-type             :animal ; node-type for relay
 :db-key                :db ; db-key to find datasource in a lacinia's context
 :pre-process-arguments #var animal.core/pre-process-arguments ; fn to process in args
 :post-process-row      #var animal.core/post-process-row ; fn to process in a result
 :filters               {:country-code #var animal.core/get-country-code} ; filters for additional filter opts for query
 :resolvers             {:resolve-node       {:table-fetcher #var animal.db/fetch} ; table fetcher for queries
                         :resolve-connection {:table-fetcher #var animal.db/fetch
                                              :settings      {:auth               #var animal.core/auth ; set for authentification and authorization.
                                                              :kebab-case?        true ; opts ; transform args into kebeb-case. A default is true.
                                                              :return-camel-case? true}} ; opts ; transform results into camelCase. A default value is true.
                         :resolve-by-fk      {:superfetcher #var animal.superfetcher/->Fetch ; superfetcher for superlifter
                                              :fk-in-parent :user-type-id}
                         :resolve-create-one {:table-fetcher #var animal.db/fetch
                                              :mutation-fn   #var animal.core/create-one 
                                              :mutation-tag  AnimalPayload}}}
```

# Getting Started
Let's start with creating a simple resolver containing queries for connection, connection by a foreign key (1:N), a single record by a foreign key (1:1).
```edn
{:target-ns my-animal.resolve
 :node-type :my-animal
 :db-key    :db
 :resolvers {:resolve-node       {:table-fetcher my-animal.db/fetch}
             :resolve-connection {:table-fetcher my-animal.db/fetch}
             :resolve-by-fk      {:superfetcher my-animal.superfetcher/->Fetch
                                  :fk-in-parent :user-type-id}}}

```

Then, you might be able to think that you need to create a db-fetcher, superfetcher!
Here's things.

`db-fetcher`
```clojure
(ns my-animal.db
    (:require [my-db-util.interface :refer [unqualified-kebab-fetch!]]
              [gosura.helpers.db :as gosura-db]
              [honey.sql.helpers :as hh]))

(defn fetch
  [db
   {:keys [id ids] :as _filter-options}
   page-options]
  (let [cursor-filter-pred (gosura-db/cursor-filter-pred page-options)
        query (-> (hh/select :*)
                  (hh/from :my-animal)
                  (hh/where :and
                            cursor-filter-pred
                            (when id [:= :id id])
                            (when ids (if (seq ids) [:in :id ids] [false])))
                  (gosura-db/add-page-options page-options))]
    (unqualified-kebab-fetch! db query))) ; TODO unqualified-kebab-fetch! will be in gosura?
```

`superfetcher`
```clojure
(ns my-animal.superfetcher)

(ns my-animal.superfetcher
  (:require [gosura.helpers.superlifter :refer [superfetcher]]))

(superfetcher Fetch {:db-key        :db
                     :table-fetcher my-animal.db/fetch
                     :id-column     :id
                     :filter-key    :ids})
```

`generate-all` must be executed before lacinia compiles schema because `generate-all` has to make all the resolvers in edn where we define. In order to use a tagged literal `#var`, make sure you need to use `gosura.edn/read-config`.
```clojure
(require '[gosura.core :as gosura]
          [gosura.edn])

(defn- read-in-resource [path]
  (-> path
      clojure.java.io/resource
      gosura.edn/read-config))

(def gosura-resolvers
  (->> ["resolver/animal.edn"]
       (map read-in-resource)))

(gosura/generate-all gosura-resolvers)
```

When your server is executed, lacinia is run successfully with generated resolvers.

# API docs
See [docs](API.md). You can call and make a quickdoc using
```clojure
clj -M:quickdoc
```
# Status
- not stable

# Todo
- documenations for lacinia compiler to use symbolic resolve in schema.
- documenations for lacinia schema examples

# Deploy
## Clojars

출시하고자 하는 버전에 해당하는 git tag를 생성하고, `build.edn` 파일을 수정한 뒤 아래 명령어를 실행합니다.

환경변수에 `CLOJARS_USERNAME`와 `CLOJARS_PASSWORD`가 지정되어 있어야 합니다.

```bash
clj -T:build deploy
```

# License
