(ns church-calendar-sync.core 
  (:require
   [clojure.core.match :refer [match]]
   [hiccup2.core :as h]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]))

(defn page [html] 
  {:status 200 
   :headers {"Content-Type" "text/html"}
   :body html})

(def main-view
  (->
   [:body
    [:div "Hello World"]]
   h/html
   str))

(def ^:const port 8888)
(def ^:const main-view-path "/main")

(defn -main [& args]
  (jetty/run-jetty (fn [{:keys [request-method uri] :as req}]
                     (match [request-method uri]
                       [:get main-view-path] (page main-view)
                       :else (response/not-found "Not found")))
                   {:port port :join? false})
  (clojure.java.browse/browse-url (str "http://localhost:" port main-view-path)))


;; {:ssl-client-cert nil, :protocol HTTP/1.1, :remote-addr [0:0:0:0:0:0:0:1], :headers {sec-fetch-site none, sec-ch-ua-mobile ?0, host localhost:8888, user-agent Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36, sec-fetch-user ?1, sec-ch-ua "Brave";v="149", "Chromium";v="149", "Not)A;Brand";v="24", sec-ch-ua-platform "Linux", connection keep-alive, upgrade-insecure-requests 1, accept text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8, accept-language en-US,en;q=0.6, sec-fetch-dest document, accept-encoding gzip, deflate, br, zstd, sec-fetch-mode navigate, sec-gpc 1}, :server-port 8888, :content-length nil, :content-type nil, :character-encoding nil, :uri /, :server-name localhost, :query-string nil, :body #object[org.eclipse.jetty.ee9.nested.HttpInput 0x2e4c7059 oeje9n.HttpInput@776761433 cs=oeje9n.HttpChannelState@5637814e{s=HANDLING rs=BLOCKING os=OPEN is=IDLE awp=false se=false i=true al=0} cp=org.eclipse.jetty.ee9.nested.BlockingContentProducer@71e64704 eof=false], :scheme :http, :request-method :get}
;; {:ssl-client-cert nil, :protocol HTTP/1.1, :remote-addr [0:0:0:0:0:0:0:1], :headers {sec-fetch-site same-origin, sec-ch-ua-mobile ?0, host localhost:8888, user-agent Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36, sec-ch-ua "Brave";v="149", "Chromium";v="149", "Not)A;Brand";v="24", sec-ch-ua-platform "Linux", referer http://localhost:8888/, connection keep-alive, accept image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8, accept-language en-US,en;q=0.6, sec-fetch-dest image, accept-encoding gzip, deflate, br, zstd, sec-fetch-mode no-cors, sec-gpc 1}, :server-port 8888, :content-length nil, :content-type nil, :character-encoding nil, :uri /favicon.ico, :server-name localhost, :query-string nil, :body #object[org.eclipse.jetty.ee9.nested.HttpInput 0x2e4c7059 oeje9n.HttpInput@776761433 cs=oeje9n.HttpChannelState@5637814e{s=HANDLING rs=BLOCKING os=OPEN is=IDLE awp=false se=false i=true al=0} cp=org.eclipse.jetty.ee9.nested.BlockingContentProducer@71e64704 eof=false], :scheme :http, :request-method :get}
