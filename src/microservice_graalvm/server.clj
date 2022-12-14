(ns microservice-graalvm.server
  (:require [aero.core :as aero]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [exoscale.interceptor :as ix]
            [java-http-clj.core :as http]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [org.httpkit.server :as http-kit]
            [ring.util.codec :as codec]
            [ruuter.core :as ruuter]
            [schema-tools.coerce :as stc]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [taoensso.timbre :as timbre])
  (:import [com.zaxxer.hikari HikariDataSource])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn ^:private get-content-type [ctx]
  (or (get (:headers ctx) "content-type")
      (get (:headers ctx) "Content-Type")
      ""))

;; Parse Json Body Interceptors
(def parse-body
  {:name :parse-request-body
   :enter
   (-> (fn [ctx] (assoc ctx
                        :body (-> ctx :body slurp (json/decode true))))
       (ix/when #(and (when-let [body-type (-> % :body type)] 
                        (or (= body-type java.io.ByteArrayInputStream)
                            (= body-type org.httpkit.BytesInputStream)))
                      (string/includes? (get-content-type %) "application/json"))))
   :leave
   (-> (fn [ctx] (-> ctx
                     (assoc :headers {"content-type" "application/json"})
                     (assoc :body (-> ctx :body (json/encode true)))))
       (ix/when #(and (or (= (-> % :body type) clojure.lang.PersistentArrayMap)
                          (= (-> % :body type) clojure.lang.PersistentVector))
                      (or (string/blank? (get-content-type %))
                          (string/includes? (get-content-type %)
                                            "application/json")))))
   :error (fn [ctx err]
            (timbre/log :error :parse-request-body err ctx)
            {:status 500 :body (str err)})})

(def parse-query
  {:name :parse-request-query
   :enter (-> (fn [ctx] (assoc ctx
                               :query (->> ctx
                                           :query-string
                                           codec/form-decode
                                           walk/keywordize-keys)))
              (ix/when #(not (-> % :query-string nil?))))
   :error (fn [ctx err]
            (timbre/log :error :parse-request-query err ctx)
            {:status 500 :body (str err)})})

(defn ^:private schema-coercer
  [schema matcher data]
  (let [parse (coerce/coercer! schema matcher)
        coerced (parse data)]
    (s/validate schema coerced)
    coerced))

;; Schema Coercer Interceptors
(def coerce-schema
  {:name :coerce-request-schema
   :enter (fn [{:keys [params body query parameters] :as ctx}]
            (let [str-matcher stc/string-coercion-matcher
                  body-matcher stc/json-coercion-matcher
                  parsed-query (when-let [query-schema (:query parameters)]
                                 (schema-coercer query-schema str-matcher query))
                  parsed-path (when-let [path-schema (:path parameters)]
                                (schema-coercer path-schema str-matcher params))
                  parsed-body (when-let [body-schema (:body parameters)]
                                (schema-coercer body-schema body-matcher body))]
              (cond-> ctx
                (not-empty parsed-query) (assoc :query parsed-query)
                (not-empty parsed-path) (assoc :params parsed-path)
                (not-empty parsed-body) (assoc :body parsed-body))))
   :leave (fn [{:keys [response responses] :as ctx}]
            (if-let [schema (get responses (:status response))]
              (->> response
                   (schema-coercer (assoc schema :status s/Int)
                                   stc/json-coercion-matcher)
                   (merge ctx))
              ctx))
   :error (fn [ctx err]
            (timbre/log :warn :coerce-request-schema err ctx)
            {:status 400
             :body (str (ex-data err))})})

;; Interstate
(defn ^:private process-route-response
  [{:keys [handler handler-error interceptors parameters responses]}]
  (fn [req]
    (ix/execute req
                (concat [{:name :prepare-ctx
                          :enter (fn [ctx] (assoc ctx
                                                  :parameters parameters
                                                  :responses responses))
                          :leave (fn [ctx] (dissoc ctx
                                                   :exoscale.interceptor/queue
                                                   :exoscale.interceptor/stack
                                                   :responses))}]
                        (or (-> req :interceptors) [])
                        interceptors
                        [{:name :handler-fn
                          :error (or handler-error
                                     (-> req :handler-error)
                                     (fn [_ctx err]
                                       {:status 500 :body (str err)}))
                          :enter (-> handler
                                     (ix/in [])
                                     (ix/out [:response]))
                          :leave (fn [ctx]
                                   (-> (select-keys ctx [:exoscale.interceptor/queue
                                                         :exoscale.interceptor/stack
                                                         :responses])
                                       (merge (:response ctx))))}]))))

(defn routes->ruuter [routes]
  (map #(assoc % :response (process-route-response %)) routes))

(defn routes->handler
  ([routes]
   (routes->handler routes {}))
  ([routes ctx]
   (fn [request]
     (ruuter/route (routes->ruuter routes)
                   (merge request ctx)))))

;; Db helper
(defn db-query []
  (let [config (-> (io/resource "config.edn")
                   (aero/read-config {:profile :dev})
                   :database)]
    (with-open [^HikariDataSource ds (connection/->pool HikariDataSource config)]
      (.close (jdbc/get-connection ds))
      (-> ds
          (jdbc/execute! ["SELECT * FROM pg_catalog.pg_tables WHERE tablename = 'pg_index';"])
          first))))

;; Routes
(def routes
  [{:path "/"
    :method :get
    :handler (fn [_ctx]
               (let [price (-> {:uri "https://api.coindesk.com/v1/bpi/currentprice.json"
                                :method :get}
                               (http/send {})
                               :body
                               (json/decode true)
                               :bpi
                               :USD
                               :rate)]
                 {:status 200
                  :body (str "Hi there!!!! The current price is "
                             price
                             " your db schema is "
                             (:pg_tables/schemaname (db-query)))}))
    :interceptors [{:enter #(do (timbre/log :info "1" %) %)}
                   {:enter #(do (timbre/log :info "2" %) %)}]}

   {:path "/api/endpoint"
    :method :post
    :parameters {:body {:who s/Str}}
    :responses {200 {:body s/Str}}
    :handler (fn [ctx]
               {:status 200
                :body (str "Hello, " (-> ctx :body))})
    :interceptors [{:enter #(do (timbre/log :info "1" %) %)}
                   {:enter #(do (timbre/log :info "2" %) %)}]}

   {:path "/hello/:who/:times"
    :method :get
    :parameters {:path {:who s/Str
                        :times s/Int}}
    :responses {200 {:body {:message s/Str
                            :who s/Str
                            :times s/Int}}}
    :handler (fn [ctx]
               {:status 200
                :body {:message "hello"
                       :who (-> ctx :params :who)
                       :times (-> ctx :params :times)}})
    :interceptors [{:enter #(do (timbre/log :info "1" %) %)}
                   {:enter #(do (timbre/log :info "2" %) %)}]}])

(def base-interceptors [parse-body parse-query coerce-schema])

(def route-handlers
  (routes->handler routes {:interceptors base-interceptors
                           :handler-error (fn [ctx err]
                                            (timbre/log :error err ctx)
                                            {:status 500 :body (str err)})}))

;; Server
(defonce server (atom nil))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& _args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and https://http-kit.github.io/migration.html#reload
  (reset! server (http-kit/run-server #'route-handlers {:port 8080})))

;; Tests
(comment
  (require '[ring.mock.request :as mock])
  (->> (mock/request :get "/hello/rafa/1")
       route-handlers)
  (->> (-> (mock/request :post "/api/endpoint")
           (merge {:headers {"a" "b"}})
           (mock/json-body {:who "delboni"}))
       route-handlers)
  (->> (mock/request :get "/")
       route-handlers))
