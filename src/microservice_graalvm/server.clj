(ns microservice-graalvm.server
  (:require [cheshire.core :as json]
            [clojure.string :as string]
            [exoscale.interceptor :as ix]
            [java-http-clj.core :as http]
            [org.httpkit.server :as http-kit]
            [ruuter.core :as ruuter])
  (:gen-class))

(def parse-request-body
  {:name :parse-request-body
   :enter
   (-> (fn [ctx] (assoc ctx :body
                        (-> ctx :body slurp (json/decode true))))
       (ix/when #(and (= (-> % :body type) java.io.ByteArrayInputStream)
                      (= (:content-type %) "application/json"))))
   :error (fn [_ctx err] {:status 500 :body (str err)})})

(def parse-response-body
  {:name :parse-response-body
   :enter (-> (fn [ctx] (-> ctx
                            (assoc :content-type "application/json")
                            (assoc :body (-> ctx :body (json/encode true)))))
              (ix/when #(and (= (-> % :body type) clojure.lang.PersistentArrayMap)
                             (or (string/blank? (:content-type %))
                                 (= (:content-type %) "application/json")))))
   :error (fn [_ctx err] {:status 500 :body (str err)})})

(def base-interceptors {:before [parse-request-body]
                        :after [parse-response-body]})

(defn ps "Process route response"
  [{:keys [response-fn interceptors]}]
  (fn [req]
    (ix/execute req
                (concat (-> req :interceptors :before)
                        interceptors
                        [{:name :response-fn
                          :error (fn [_ctx err] {:status 500 :body (str err)})
                          :enter (-> response-fn
                                     (ix/in [])
                                     (ix/out [:response]))}
                         {:name :response-fn-after
                          :error (fn [_ctx err] {:status 500 :body (str err)})
                          :enter (fn [ctx]
                                   (-> ctx
                                       (merge (:response ctx))
                                       (dissoc :response
                                               :interceptors)))}]
                        (-> req :interceptors :after)))))

(def routes
  [{:path "/"
    :method :get
    :response (ps {:response-fn (fn [_ctx]
                                  (let [price (-> {:uri "https://api.coindesk.com/v1/bpi/currentprice.json" :method :get}
                                                  (http/send {})
                                                  :body
                                                  (json/decode true)
                                                  :bpi
                                                  :USD
                                                  :rate)]
                                    {:status 200
                                     :body (str "Hi there!!!! The current price is " price)}))
                   :interceptors [{:enter #(do (println "1" %) %)}
                                  {:enter #(do (println "2" %) %)}]})}
   {:path "/api/endpoint"
    :method :post
    :response (ps {:response-fn (fn [ctx]
                                  {:status 200
                                   :body (str "Hello, " (-> ctx :body :who))})
                   :interceptors [{:enter #(do (println "1" %) %)}
                                  {:enter #(do (println "2" %) %)}]})}
   {:path "/hello/:who/:times"
    :method :get

    :response (ps {:response-fn (fn [ctx]
                                  {:status 200
                                   :body {:message "hello"
                                          :who (-> ctx :params :who)
                                          :times (-> ctx :params :times)}})
                   :interceptors [{:enter #(do (println "1" %) %)}
                                  {:enter #(do (println "2" %) %)}]})}])

(defonce server (atom nil))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn route-handler [request]
  (ruuter/route routes (merge request {:interceptors base-interceptors})))

(defn -main [& _args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and https://http-kit.github.io/migration.html#reload
  (reset! server (http-kit/run-server route-handler {:port 8080})))

(comment
  (require '[ring.mock.request :as mock])
  (->> (mock/request :get "/hello/rafa/3")
       route-handler)
  (->> (-> (mock/request :post "/api/endpoint")
           (mock/json-body {:who "delboni"}))
       route-handler)
  (->> (mock/request :get "/")
       route-handler))
