(ns church-calendar-sync.core
  (:require
   [church-calendar-sync.app :as app]
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.google.oauth.storage :as storage]
   [church-calendar-sync.utils :refer [match=]]
   [clojure.java.browse :as browse]
   [clojure.spec.alpha :as s]
   [hiccup2.core :as h]
   [org.httpkit.server :as server]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]))

(defn page [html]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (if (vector? html)
           (str (h/html html))
           html)})

(def ^:const upload-view-path "/ods-upload")

(def ^:const main-view-path "/main")

(def oauth-creds (delay (oauth/web-credentials "credentials.json")))

;; todo real storage lol
(defonce google-auth (atom nil))
(extend-protocol storage/TokenStorage
  clojure.lang.Atom
  (-get [a] (deref a))
  (-put [a item] (reset! a item)))

(defn- -base-app-handler
  [creds]
  (s/assert ::oauth/creds creds)
  (let [oauth-redirect-path (oauth/local-redirect-path creds)
        app-context (assoc creds :upload-path upload-view-path :token-storage google-auth)]
    (println 'oauth-redirect-path " " oauth-redirect-path)
    (fn [{:keys [request-method uri] :as req}]
      (println request-method uri (:params req))
      (match= [request-method uri] 
              [:get main-view-path] (page (app/main app-context))
              [:get oauth-redirect-path] (page (app/oauth-get-token app-context req))
              [:post upload-view-path] (page (app/processing-upload req))
              (response/not-found "Not found")))))

(defn app [creds]
  (-> (-base-app-handler creds) wrap-params wrap-multipart-params))

;; to be able to shut down in repl testing
(defonce server (atom nil))

(defn -main [& args]
  (let [creds @oauth-creds
        port (oauth/local-port creds)]
    (reset! server (server/run-server (app creds) {:port port :join? false}))
    (browse/browse-url (str "http://localhost:" port main-view-path))))

(comment
  (do
    (clojure.spec.alpha/check-asserts true)
    (when-let [s @server] (s))  (-main)) 
  )
