(ns church-calendar-sync.google.gcal

  (:require
   [church-calendar-sync.google.oauth :refer [repl-login]]
   [clj-http.client :as client]
   [clojure.data.json :as json]))


(defn events
  [calendar-id {:strs [access_token token_type]}]
  (client/get (str "https://www.googleapis.com/calendar/v3/calendars/" calendar-id "/events")
              {:headers {"Authorization" (str token_type " " access_token)}
               :content-type :json
               :accept :json}))

(def primary-events (partial events "primary"))

(repl-login)

(def result @*1)

token-results

(def token-results (json/read-str (:body result)))

(primary-events token-results)

(def many-events (json/read-str (:body *1) :key-fn decode-key))

(last (:items many-events))