(ns church-calendar-sync.google.oauth 
  (:require
    [camel-snake-kebab.core :as csk]
    [org.httpkit.client :as client]
    [clojure.data.json :as json]
    [clojure.java.browse :as browse]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [ring.adapter.jetty :as jetty]))

(def decode-key csk/->kebab-case-keyword)

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
      (json/read-str :key-fn decode-key)))

(defn- ring-req->oauth-code [request]
  (-> request
      :query-string
      (str/split #"&scope=")
      first
      (str/split #"&code=")
      last ;; hacky! relies on order
      ring.util.codec/url-decode))

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

(defn- start-server! [oauth-promise creds]
  (s/assert ::token-request-creds creds)
  (reset! server
          (jetty/run-jetty (tmp-oauth-handler oauth-promise creds)
                           {:port (->> creds 
                                       :redirect-uris
                                       (filter #(str/includes? % "localhost")) 
                                       first
                                       (java.net.URL.)
                                       (.getPort))
                            :join? false})))

(defn stop-server! []
  (swap! server (fn [s] (when s (.stop s)) nil)))

(s/def ::creds (s/merge ::token-request-creds ::login-url-creds))

(defn- web-credentials [creds-resource-path]
  {:post [(s/assert ::creds %)]}
  (-> creds-resource-path
      io/resource
      slurp
      (json/read-str :key-fn decode-key)
      (:web)))

(s/def ::access-token (s/and string? #(> (count %) 300)))
(s/def ::expires-in pos-int?)
(s/def ::scope uri-str?) ;; todo: this may not be right, could end up being csv of scopes or something? Or maybe need a new token per scope?
(s/def ::token-type #{"Bearer"})

(s/def ::token-result (s/keys :req-un [::access-token ::expires-in ::scope ::token-type]))

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

