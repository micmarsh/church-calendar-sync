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
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]
   [time-literals.read-write])
  (:gen-class))
(time-literals.read-write/print-time-literals-clj!)


(defn page [value]
  (if (map? value)
    value
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (if (vector? value)
             (str (h/html value))
             value)}))

(def oauth-creds (delay (oauth/web-credentials "credentials.json")))

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

             (response/not-found "Not found")))))

(defn ->app [creds]
  (let [storage @storage/storage-file
        ctx (assoc creds :token-storage storage :config-storage storage)] 
    (-> (-base-app-handler ctx) wrap-params wrap-multipart-params)))

;; to be able to shut down in repl testing
(defonce server (atom (fn [])))

(defn -main [& args]
  (clojure.spec.alpha/check-asserts true)
  (let [creds @oauth-creds
        port (oauth/local-port creds)]
    (reset! server (server/run-server (->app creds) {:port port :join? false}))
    (browse/browse-url (str "http://localhost:" port app/main-view-path))))

(comment
  (do
    (@server)
    (-main))
  )

