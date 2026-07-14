(ns church-calendar-sync.gcal

  (:require
   [clj-http.client :as client]
   [ring.adapter.jetty :as jetty]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(defn oauth-req-options [redirect-uri client-id]
  {:query-params {"response_type" "code"
                  "redirect_uri" redirect-uri
                  "client_id" client-id
                  "scope" "https://www.googleapis.com/auth/calendar"}})

(defn- query-params->string [options]
  (str/join "&" (map #(str (key %) "=" (val %)) options)))

(defn get-raw-oath-url [{:strs [client_id redirect_uris auth_uri]}]
  (let [http-options (oauth-req-options (first redirect_uris) client_id)]
    (str auth_uri "?" (query-params->string (:query-params http-options)))))

;; creds: {str -> str} map, from "web" in parsed file
;; lots of potential for reader monads here!
(defn oauth-token
  [auth-code {:strs [client_id client_secret redirect_uris token_uri] :as creds}]
  (client/post token_uri
               {:content-type :x-www-form-urlencoded
                :accept :json
                :form-params {"code" auth-code
                              "client_id" client_id
                              "client_secret" client_secret
                              "redirect_uri" (first redirect_uris) ;; need a better way to get local vs. prod, 
                              "grant_type" "authorization_code"}}))

(defn events
  [calendar-id {:strs [access_token token_type]}]
  (client/get (str "https://www.googleapis.com/calendar/v3/calendars/" calendar-id "/events")
              {:headers {"Authorization" (str token_type " " access_token)}
               :content-type :json
               :accept :json}))

(def primary-events (partial events "primary"))

(defn tmp-oauth-handler [oauth-promise creds]
  (fn [request]
    (pprint/pprint request)
    (let [code (-> request
                   :query-string
                   (str/split #"&scope=")
                   first
                   (str/split #"&code=")
                   last ;; hacky! relies on order
                   ring.util.codec/url-decode)]
      (try 
        (let [token-resp (oauth-token code creds)]
          (deliver oauth-promise token-resp)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (str token-resp)})
        (catch Exception ex
          (deliver oauth-promise ex)
          (throw ex))))))

(def server (atom nil))

(defn- start-server! [oauth-promise creds]
  (reset! server
          (jetty/run-jetty (tmp-oauth-handler oauth-promise creds)
                           {:port 23456 ;; todo pull from creds!?
                            :join? false})))

(defn stop-server! []
  (swap! server (fn [s] (when s (.stop s)) nil)))

(defn- web-credentials [creds-resource-path]
  (-> creds-resource-path
      io/resource
      slurp
      json/read-str
      (get "web")))

(defn repl-login []
  (let [creds-file-json (web-credentials "credentials.json")
        oauth-promise (promise)
        _ (start-server! oauth-promise creds-file-json)
        _ (clojure.java.browse/browse-url (get-raw-oath-url creds-file-json))]
    (reify clojure.lang.IDeref
      (deref [_] 
        (let [result @oauth-promise]
          (stop-server!)
          result)))))
