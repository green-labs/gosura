(ns gosura.schema)

(def resolver-settings-schema
  [:map {:closed true}
   [:auth {:optional true} [:or [:or symbol? [:fn var?]] [:cat [:or symbol? [:fn var?]] [:* any?]]]]
   [:kebab-case? {:optional true} boolean?]
   [:return-camel-case? {:optional true} boolean?]])


(def base-mutation-config-schema
  [:map
   [:settings {:optional true} resolver-settings-schema]
   [:node-type {:optional true} keyword?]
   [:db-key {:optional true} keyword?]
   [:post-process-row {:optional true} [:or symbol? [:fn var?]]]
   [:pre-process-arguments {:optional true} [:or symbol? [:fn var?]]]
   [:table-fetcher [:or symbol? [:fn var?]]]
   [:mutation-fn [:or symbol? [:fn var?]]]
   [:mutation-tag keyword?]])

(def base-root-query-multi-config-schema
  [:map
   [:settings {:optional true} resolver-settings-schema]
   [:node-type {:optional true} keyword?]
   [:db-key {:optional true} keyword?]
   [:post-process-row {:optional true} [:or symbol? [:fn var?]]]
   [:pre-process-arguments {:optional true} [:or symbol? [:fn var?]]]
   [:return-camel-case? {:optional true} boolean?]
   [:table-fetcher [:or symbol? [:fn var?]]]])

(def base-root-query-one-config-schema
  [:map
   [:settings {:optional true} resolver-settings-schema]
   [:node-type {:optional true} keyword?]
   [:db-key {:optional true} keyword?]
   [:post-process-row {:optional true} [:or symbol? [:fn var?]]]
   [:pre-process-arguments {:optional true} [:or symbol? [:fn var?]]]
   [:return-camel-case? {:optional true} boolean?]
   [:fetch-one [:or symbol? [:fn var?]]]])

(def resolvers-map-schema
  "resolvers 설정에 필요한 스키마 정의"
  [:map {:closed true}
   [:target-ns [:or symbol? [:fn var?]]]
   [:node-type keyword?]
   [:db-key keyword?]
   [:post-process-row {:optional true} [:or symbol? [:fn var?]]]
   [:filters {:optional true} map?]
   [:pre-process-arguments {:optional true} [:or symbol? [:fn var?]]]
   [:settings {:optional true} resolver-settings-schema]
   [:return-camel-case? {:optional true} boolean?]
   [:resolvers [:map
                [:resolve-connection {:optional true} base-root-query-multi-config-schema]
                [:resolve-one {:optional true} base-root-query-one-config-schema]
                [:resolve-by-fk {:optional true} [:map
                                                  [:settings {:optional true} resolver-settings-schema]
                                                  [:node-type {:optional true} keyword?]
                                                  [:db-key {:optional true} keyword?]
                                                  [:post-process-row {:optional true} [:or symbol? [:fn var?]]]
                                                  [:return-camel-case? {:optional true} boolean?]
                                                  [:superfetcher [:or symbol? [:fn var?]]]
                                                  [:fk-in-parent keyword?]]]
                [:resolve-create-one {:optional true} base-mutation-config-schema]
                [:resolve-update-one {:optional true} base-mutation-config-schema]
                [:resolve-update-multi {:optional true} base-mutation-config-schema]
                [:resolve-delete-one {:optional true} [:map
                                                       [:settings {:optional true} resolver-settings-schema]
                                                       [:db-key {:optional true} keyword?]
                                                       [:mutation-fn [:or symbol? [:fn var?]]]]]]]])

(def node-schema
  [:map
   [:node-type keyword?]
   [:db-id [:or string? int?]]])

(def decoded-id-schema
  [:map
   [:node-type keyword?]
   [:db-id string?]])
