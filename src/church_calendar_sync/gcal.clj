(ns church-calendar-sync.gcal

  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clj-http.client :as client]
   [clojure.string :as str]))


(def creds (json/read-str (slurp (io/resource "credentials.json"))))

(def redirect-uri (get-in creds ["installed" "redirect_uris" 0]))
(def client-id (get-in creds ["installed" "client_id"]))
(def client-secret (get-in creds ["installed" "client_secret"]))

(def ^:const google-oauth-uri
  "https://accounts.google.com/o/oauth2/v2/auth")

(defn oauth-req-options [redirect-uri client-id]
  {:query-params {"response_type" "code"
                  "redirect_uri" redirect-uri
                  "client_id" client-id
                  "scope" "https://www.googleapis.com/auth/calendar"}})

#_(def response (future (client/get google-oauth-uri (options redirect-uri client-id))))

(defn- query-params->string [options]
  (str/join "&" (map #(str (key %) "=" (val %)) options)))

(defn get-raw-oath-url [creds-resource-uri]
  (let [creds (json/read-str (slurp creds-resource-uri))
        redirect-uri (get-in creds ["installed" "redirect_uris" 0])
        client-id (get-in creds ["installed" "client_id"])
        http-options (oauth-req-options redirect-uri client-id)]
    (str google-oauth-uri "?" (query-params->string (:query-params http-options)))))

(clojure.java.browse/browse-url
 (get-raw-oath-url (io/resource "credentials.json")))

;; creds: {str -> str} map, from "installed" in parsed file
;; lots of potential for reader monads here!
(defn oath-token
  [auth-code {:strs [client_id client_secret redirect_uris] :as creds}]
  (client/post "https://oauth2.googleapis.com/token"
               {:content-type :x-www-form-urlencoded
                :accept :json
                :form-params {"code" auth-code
                              "client_id" client_id
                              "client_secret" client_secret
                              "redirect_uri" (first redirect_uris) ;; need a better way to get local vs. prod, 
                              "grant_type" "authorization_code"}}))

(defn events
  [calendar-id oauth-token]
  (client/get (str "https://www.googleapis.com/calendar/v3/calendars/" calendar-id "/events")
              {:headers {"Authorization" (str "Bearer " oauth-token)}
               :content-type :json
               :accept :json}))

(def primary-events (partial events "primary"))

(defn repl-login []
  (let [creds-uri (io/resource "credentials.json")
        oauth-promise (promise)
        _ (start-server! oauth-promise creds-uri)
        _ (clojure.java.browse/browse-url (get-raw-oath-url creds-uri))
        oauth-token-resp @oauth-promise
        _ (stop-server!)]
    oauth-token-resp))
