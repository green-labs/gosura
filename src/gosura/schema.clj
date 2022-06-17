(ns gosura.schema)

(def resolver-settings-schema
  [:map {:closed true}
   [:auth {:optional true} [:or symbol? [:cat symbol? [:* any?]]]]
   [:kebab-case? {:optional true} boolean?]
   [:return-camel-case? {:optional true} boolean?]])

(def resolvers-map-schema
  "resolvers 설정에 필요한 스키마 정의"
  [:map {:closed true}
   [:target-ns symbol?]
   [:node-type keyword?]
   [:db-key keyword?]
   [:post-process-row {:optional true} symbol?]
   [:filters {:optional true} map?]
   [:pre-process-arguments {:optional true} symbol?]
   [:resolvers [:map
                [:resolve-connection {:optional true} [:map
                                                       [:settings {:optional true} resolver-settings-schema]
                                                       [:node-type {:optional true} keyword?]
                                                       [:db-key {:optional true} keyword?]
                                                       [:post-process-row {:optional true} symbol?]
                                                       [:pre-process-arguments {:optional true} symbol?]
                                                       [:table-fetcher symbol?]]]
                [:resolve-by-fk {:optional true} [:map
                                                  [:settings {:optional true} resolver-settings-schema]
                                                  [:node-type {:optional true} keyword?]
                                                  [:db-key {:optional true} keyword?]
                                                  [:post-process-row {:optional true} symbol?]
                                                  [:superfetcher symbol?]
                                                  [:fk-in-parent keyword?]]]
                [:resolve-create-one {:optional true} [:map
                                                       [:settings {:optional true} resolver-settings-schema]
                                                       [:node-type {:optional true} keyword?]
                                                       [:db-key {:optional true} keyword?]
                                                       [:post-process-row {:optional true} symbol?]
                                                       [:pre-process-arguments {:optional true} symbol?]
                                                       [:table-fetcher symbol?]
                                                       [:mutation-fn symbol?]
                                                       [:mutation-tag keyword?]]]
                [:resolve-update-one {:optional true} [:map
                                                       [:settings {:optional true} resolver-settings-schema]
                                                       [:node-type {:optional true} keyword?]
                                                       [:db-key {:optional true} keyword?]
                                                       [:post-process-row {:optional true} symbol?]
                                                       [:pre-process-arguments {:optional true} symbol?]
                                                       [:table-fetcher symbol?]
                                                       [:mutation-fn symbol?]
                                                       [:mutation-tag keyword?]]]
                [:resolve-delete-one {:optional true} [:map
                                                       [:settings {:optional true} resolver-settings-schema]
                                                       [:db-key {:optional true} keyword?]
                                                       [:mutation-fn symbol?]]]]]])

(def node-schema
  [:map
   [:node-type keyword?]
   [:db-id [:or string? int?]]])

(def decoded-id-schema
  [:map
   [:node-type keyword?]
   [:db-id string?]])
