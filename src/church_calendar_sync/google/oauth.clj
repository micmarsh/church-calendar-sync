(ns church-calendar-sync.google.oauth
  (:require
   [church-calendar-sync.utils :refer [parse-json]]
   [clojure.java.browse :as browse]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server]
   [ring.util.codec :as codec]))

(defn oauth-req-options [redirect-uri client-id]
  {:query-params {"response_type" "code"
                  "redirect_uri" redirect-uri
                  "client_id" client-id
                  "scope" "https://www.googleapis.com/auth/calendar"}})

(defn- query-params->string [options]
  (str/join "&" (map #(str (key %) "=" (val %)) options)))

(defn uri-str? [str]
  (try 
    (java.net.URL. str)
    true
    (catch Exception e false)))

(s/def ::client-id (s/and string? #(str/ends-with? % ".apps.googleusercontent.com")))
(s/def ::client-secret (s/and string? #(= 35 (count %))))

(s/def ::redirect-uris (s/+ uri-str?))
(s/def ::auth-uri uri-str?)
(s/def ::token-uri uri-str?)

(s/def ::login-url-creds (s/keys :req-un [::client-id ::redirect-uris ::auth-uri]))

(defn get-raw-oath-url [{:keys [client-id redirect-uris auth-uri] :as creds}]
  (s/assert ::login-url-creds creds)
  (let [http-options (oauth-req-options (first redirect-uris) client-id)]
    (str auth-uri "?" (query-params->string (:query-params http-options)))))

(s/def ::token-request-creds (s/keys :req-un [::client-id ::client-secret ::redirect-uris ::token-uri]))

(defn oauth-token
  [auth-code {:keys [client-id client-secret redirect-uris token-uri] :as creds}]
  {:pre [(s/assert ::token-request-creds creds)]
   :post [(s/assert ::token-result %)]}
  (-> token-uri
      (client/post
       {:content-type :x-www-form-urlencoded
        :accept :json
        :form-params {"code" auth-code
                      "client_id" client-id
                      "client_secret" client-secret
                      "redirect_uri" (first redirect-uris) ;; need a better way to get local vs. prod, 
                      "grant_type" "authorization_code"}})
      deref
      :body
      parse-json))

(s/def :ring/query-params (s/map-of string? string?))
(s/def :ring/with-query-params (s/keys :req-un [:ring/query-params]))

(defn ring-req->oauth-code [request]
  (s/assert :ring/with-query-params request)
  (-> request
      :query-params
      (get "code")))

(defn tmp-oauth-handler [oauth-promise creds]
  (s/assert ::token-request-creds creds)
  (fn [request]
    (let [code (ring-req->oauth-code request)]
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

(def ^:private has-redirect-uris
  (s/keys :req-un [::redirect-uris]))

(defn- local-url-obj [creds]
  (->> creds
       :redirect-uris
       (filter #(str/includes? % "localhost"))
       first
       (java.net.URL.)))

(defn local-port [creds]
  (s/assert has-redirect-uris creds)
  (->> creds
       (local-url-obj)
       (.getPort)))

(defn local-redirect-path [creds]
  (s/assert has-redirect-uris creds)
  (-> creds (local-url-obj) (.getPath) 
      ;; likely won't need this after adjusting credentials at some point
      (#(if (empty? %) "/" %))))

(defn- start-server! [oauth-promise creds]
  (s/assert ::token-request-creds creds)
  (reset! server
          (server/run-server (tmp-oauth-handler oauth-promise creds)
                             {:port (local-port creds)
                              :join? false})))

(defn stop-server! []
  (swap! server (fn [s] (when s (s)) nil)))

(s/def ::creds (s/merge ::token-request-creds ::login-url-creds))

(defn web-credentials [creds-resource-path]
  {:post [(s/assert ::creds %)]}
  (-> creds-resource-path
      io/resource
      slurp
      (parse-json)
      (:web)))

(s/def ::access-token (s/and string? #(> (count %) 300)))
(s/def ::expires-in pos-int?)
(s/def ::scope uri-str?) ;; todo: this may not be right, could end up being csv of scopes or something? Or maybe need a new token per scope?
(s/def ::token-type #{"Bearer"})

(s/def ::req-auth-parts (s/keys :req-un [::access-token ::token-type]))

(s/def ::token-result (s/merge ::req-auth-parts (s/keys :req-un [::expires-in ::scope])))

(s/def ::expires #(instance? java.time.LocalDateTime %))
(s/def ::expiring-token-result (s/merge (s/keys :req-un [::expires]) ::token-result))

(defn repl-login []
  (stop-server!)
  (let [creds (web-credentials "credentials.json")
        oauth-promise (promise)
        _ (start-server! oauth-promise creds)
        _ (browse/browse-url (get-raw-oath-url creds))]
    (reify clojure.lang.IDeref
      (deref [_]
        (let [result @oauth-promise]
          (stop-server!)
          (s/assert ::token-result result))))))

(comment 

  (s/check-asserts true) 

  (stop-server!)
  
  (def res (repl-login))
  
  @res

  )

