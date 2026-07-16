(ns church-calendar-sync.core
  (:require
   [church-calendar-sync.views :as views]
   [clojure.core.match :refer [match]]
   [clojure.java.browse :as browse]
   [hiccup2.core :as h]
   [ring.util.response :as response]
   [org.httpkit.server :as hk-server]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]))

(defn page [html]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (if (vector? html)
           (str (h/html html))
           html)})

(def ^:const port 8888)

(def ^:const upload-view-path "/ods-upload")

(def ^:const main-view-path "/main")

(defn- -base-app-handler [{:keys [request-method uri] :as req}]
  (match [request-method uri]
    [:get main-view-path] (page (views/main upload-view-path))
    [:post upload-view-path] (page (views/processing-upload req))
    :else (response/not-found "Not found")))

(def app (-> -base-app-handler wrap-params wrap-multipart-params))

;; to be able to shut down in repl testing
(defonce server (atom nil))

(defn -main [& args]
  (reset! server (hk-server/run-server app {:port port :join? false}))
  (browse/browse-url (str "http://localhost:" port main-view-path)))

(comment
  (-main)
  (@server)
  )
