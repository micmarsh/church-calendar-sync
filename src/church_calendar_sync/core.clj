(ns church-calendar-sync.core
  (:require
   [church-calendar-sync.app :as app]
   [church-calendar-sync.config-storage :refer [ConfigStorage]]
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.google.oauth.storage :as storage]
   [church-calendar-sync.utils :refer [cond=]]
   [clojure.java.browse :as browse]
   [clojure.spec.alpha :as s]
   [hiccup2.core :as h]
   [org.httpkit.server :as server]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]
   [time-literals.read-write]))
(time-literals.read-write/print-time-literals-clj!)


(defn page [html]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (if (vector? html)
           (str (h/html html))
           html)})

(def oauth-creds (delay (oauth/web-credentials "credentials.json")))

;; todo real storage lol
(defonce storage-atom (atom nil))
(extend-type clojure.lang.Atom
  storage/TokenStorage 
  (-get [a] (:token-storage (deref a)))
  (-put [a item] (swap! a assoc :token-storage item))
  ConfigStorage
  (get-config [a k] (get-in @a [:config k]))
  (put-config! [a k v] (swap! a assoc-in [:config k] v)))

(defn- -base-app-handler
  [ctx]
  (s/assert ::app/context ctx)
  (let [oauth-redirect-path (oauth/local-redirect-path ctx)]
    (fn [{:keys [request-method uri] :as req}]
      (cond= [request-method uri]
             [:get app/main-view-path] (page (app/main ctx))
             [:get oauth-redirect-path] (app/oauth-get-token ctx req)
             [:post app/upload-view-path] (page (app/processing-upload req))
             (response/not-found "Not found")))))

(defn ->app [creds]
  (let [ctx (assoc creds :token-storage storage-atom :config-storage storage-atom)] 
    (-> (-base-app-handler ctx) wrap-params wrap-multipart-params)))

;; to be able to shut down in repl testing
(defonce server (atom (fn [])))

(defn -main [& args]
  (let [creds @oauth-creds
        port (oauth/local-port creds)]
    (reset! server (server/run-server (->app creds) {:port port :join? false}))
    (browse/browse-url (str "http://localhost:" port main-view-path))))

(comment
  (do
    (clojure.spec.alpha/check-asserts true)
    (@server)
    (-main))
  )
