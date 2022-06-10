# Gosura
Data-driven GraphQL Resolvers on [lacinia](https://github.com/walmartlabs/lacinia) for Clojure.

# Motivation
We are writing diverse and repetative GraphQL queries and mutations where we are building general-purposed applications. When you follow [GraphQL](https://graphql.org/) and even [relay](https://relay.dev/), we should handle the relay specification with Clojure code. It's pretty annoying with us. So, we decided to make some helpers and [DSL](https://en.wikipedia.org/wiki/Domain-specific_language) for data-driven resolvers.

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

`generate-all` must be executed before lacinia compiles schema because `generate-all` has to make all the resolvers in edn where we define.
```clojure
(require '[gosura.core :as gosura])

(defn- read-in-resource [path]
  (-> path
      clojure.java.io/resource
      slurp
      cloure.edn/read-string))

(def gosura-resolvers
  (->> ["resolver/animal.edn"]
       (map read-in-resource)))

(gosura/generate-all gosura-resolvers)
```

When your server is executed, lacinia is run successfully with generated resolvers.

# Status
- not stable

# Todo
- documenations for lacinia compiler to use symbolic resolve in schema.
- documenations for lacinia schema examples

# License
