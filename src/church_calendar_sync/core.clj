(ns church-calendar-sync.core
  (:require
   [church-calendar-sync.app :as app]
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.storage.impls :as storage]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [cond=]]
   [clojure.java.browse :as browse]
   [clojure.spec.alpha :as s]
   [hiccup2.core :as h]
   [org.httpkit.server :as server]
   [org.httpkit.client :as client]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]
   [time-literals.read-write])
  (:gen-class))
(time-literals.read-write/print-time-literals-clj!)

(s/def :ring-resp/status (s/and #(>= % 100) #(<= % 599)))
(s/def :ring-resp/headers (s/map-of string? string?))
(s/def :ring-resp/body string?) ;; could technically be also input stream, others, etc.?
(s/def ::ring-resp (s/keys :req-un [:ring-resp/status  :ring-resp/body]
                           :opt-un [:ring-resp/headers]))

(defn page [value]
  (if (s/valid? ::ring-resp value)
    value
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (if (vector? value)
             (str (h/html value))
             value)}))

(def oauth-creds (delay (oauth/web-credentials "credentials.json")))

(def ^:const health-check-path "/status")

(defn- -base-app-handler
  [ctx]
  (s/assert ::spec/req-ctx ctx)
  (let [oauth-redirect-path (oauth/local-redirect-path ctx)]
    (fn [{:keys [request-method uri] :as req}]
      (println request-method " " uri)
      (cond= [request-method uri]
             [:get app/main-view-path] (page (app/main ctx))

             [:post app/upload-view-path] (page (app/processing-upload ctx req))
             [:post app/sync-to-calendar-path] (page (app/sync-to-calendar ctx req))

             [:get app/calendar-list-path] (page (app/calendar-list ctx))
             [:post app/select-calendar-path] (page (app/select-calendar ctx req))

             [:get app/download-file-path] (app/download-last-file)

             [:get oauth-redirect-path] (app/oauth-get-token ctx req)

             [:get health-check-path] {:status 200 :body "running"}

             [:post app/shutdown-path] (let [message "Shutting down application server"]
                                         (println message) 
                                         (future (Thread/sleep 1000) (System/exit 0))
                                         (page [:body message]))

             (response/not-found "Not found")))))

(defn ->app [creds]
  (let [storage @storage/storage-file
        ctx (assoc creds :token-storage storage :config-storage storage)] 
    (-> (-base-app-handler ctx) wrap-params wrap-multipart-params)))

(defonce server (atom (fn [] ::not-running)))

(defn- already-running? [base-address]
  (try 
    (or (not= ::not-running (@server))
        (= 200 (:status @(client/get (str base-address health-check-path)))))
    (catch Exception e 
      false)))

(defn -main [& args]
  (clojure.spec.alpha/check-asserts true) ;; turn off for "production" (?)
  (let [creds @oauth-creds
        port (oauth/local-port creds)
        base-address (str "http://localhost:" port)
        open #(browse/browse-url (str base-address app/main-view-path))]
    (if (already-running? base-address)
      (do 
        (println "Application is already running")
        (open))
      (do 
        (println "Starting application server at " base-address)
        (reset! server (server/run-server (->app creds) {:port port :join? false}))
        (open)))))

(comment
  (do
    (@server)
    (reset! server (fn [] ::not-running))
    (-main))
  )

