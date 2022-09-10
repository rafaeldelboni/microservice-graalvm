(ns microservice-graalvm.server
  (:require [org.httpkit.server :as http-kit]
            [exoscale.interceptor :as ix]
            [cheshire.core :as json]
            [ruuter.core :as ruuter])
  (:gen-class))

(def interceptor-parse-request-body
  {:name :parse-request-body
   :enter
   (-> (fn [ctx] (assoc-in ctx
                           [:params :body]
                           (-> ctx :body slurp (json/decode true))))
       (ix/when #(and (= (-> % :body type)
                         java.io.ByteArrayInputStream)
                      (= (:content-type %)
                         "application/json"))))
   :error (fn [ctx _err] ctx)})

(def interceptor-B {:name :B
                    :enter (fn [ctx] (update ctx :b inc))
                    :error (fn [ctx _err] ctx)})

(def interceptor-D {:name :D
                    :enter (fn [ctx] (update ctx :d inc))})

(def base-interceptors {:before [interceptor-parse-request-body interceptor-B]
                        :after [interceptor-D]})

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
                                     (ix/out [:response]))
                          :leave (fn [ctx] (-> ctx
                                               (merge (:response ctx))
                                               (dissoc :response
                                                       :interceptors)))}]
                        (-> req :interceptors :after)))))

(def routes
  [{:path "/"
    :method :get
    :response (ps {:response-fn (fn [ctx]
                                  {:status 200
                                   :body (str "Hi there!!!! " ctx)})
                   :interceptors [{:enter #(do (println "1" %) %)}
                                  {:enter #(do (println "2" %) %)}]})}
   {:path "/api/endpoint"
    :method :post
    :response (ps {:response-fn (fn [ctx]
                                  (println "----->" (-> ctx :params :body))
                                  {:status 200
                                   :body (str "Hello, " (-> ctx :params :body :who))})
                   :interceptors [{:enter #(do (println "1" %) %)}
                                  {:enter #(do (println "2" %) %)}]})}
   {:path "/hello/:who"
    :method :get
    :response (ps {:response-fn (fn [ctx]
                                  {:status 200
                                   :body (str "Hello, " (:who (:params ctx)))})
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
  (ruuter/route routes (merge request
                              {:interceptors base-interceptors}
                              {:a 0 :b 0 :d 0})))

(defn -main [& _args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and https://http-kit.github.io/migration.html#reload
  (reset! server (http-kit/run-server route-handler {:port 8080})))

(comment
  (require '[ring.mock.request :as mock])
  (->> (mock/request :get "/hello/rafa")
       route-handler)
  (->> (-> (mock/request :post "/api/endpoint")
           (mock/json-body {:who "delboni"}))
       route-handler)
  (->> (mock/request :get "/")
       route-handler))
