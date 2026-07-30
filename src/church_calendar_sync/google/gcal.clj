(ns church-calendar-sync.google.gcal
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [parse-json serialize-json]]
   [clojure.core.async :as a]
   [clojure.spec.alpha :as s]
   [org.httpkit.client :as client])
  (:import
   [java.time ZoneId]))

(s/def ::start-date ::spec/date-time)
(s/def ::end-date ::spec/date-time)
(s/def ::date-range (s/keys :req-un [::start-date ::end-date]))

(def ^:private timezone
  (ZoneId/of "America/Detroit"))

(defprotocol RFC-3339
  (->rfc3339 [obj]))
(extend-protocol RFC-3339
  java.time.LocalDateTime
  (->rfc3339
    [local-date-time]
    (-> local-date-time
        (.atZone timezone)
        (.format java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME)))
  java.time.LocalDate
  (->rfc3339
    [local-date]
    (-> local-date
        (.atTime 0 0)
        (->rfc3339)))
  java.lang.String
  (->rfc3339
    [str]
    (let [java-obj (try (java.time.LocalDateTime/parse str)
                        (catch java.time.format.DateTimeParseException _
                          (java.time.LocalDate/parse str)))]
      (->rfc3339 java-obj))))

(def ^:const base-api
  "https://www.googleapis.com/calendar/v3/")

(defn json [{:keys [access-token token-type] :as token}]
  (s/assert ::oauth/req-auth-parts token)
  {:headers {"Authorization" (str token-type " " access-token)}
   :content-type :json
   :accept :json})

(def ^:private read-resp (comp #(update % :body parse-json) deref))

(defn get-calendars [token]
  (s/assert ::oauth/req-auth-parts token)
  (-> (str base-api "users/me/calendarList")
      (client/get (json token))
      read-resp))


(defn- query-events
  [calendar-id
   {:keys [start-date end-date page-token]}
   token]
  (-> base-api
      (str "calendars/" (client/url-encode calendar-id) "/events")
      (client/get (-> (json token)
                      (assoc :query-params
                             (cond-> {"timeMin" (->rfc3339 (.toLocalDate start-date))
                                      "timeMax" (->rfc3339 end-date)
                                      "maxResults" 500}
                               page-token (assoc "pageToken" page-token)))))))

(defn get-events
  [calendar-id params token]
  (s/assert ::date-range params)
  (s/assert ::oauth/req-auth-parts token)
  (let [fetch #(read-resp (query-events calendar-id % token))]
    
    (loop [{:keys [body] :as resp} (fetch params)
           results []]
      (let [page-token (:next-page-token body)
            new-results (concat results (:items body))]
        (if page-token
          (recur (fetch (assoc params :page-token page-token))
                 new-results)
          new-results)))))

(defn ->date-time [input]
  (try
    (java.time.ZonedDateTime/parse input)
    (catch Exception e)))

(defn ->date [input]
  (try
    (java.time.LocalDate/parse input)
    (catch Exception e)))

;;todo move these..., to gcal?
(s/def :google-json/date-time ->date-time)

(defn eastern? [tz-str]
  (try
    (let [short-name (.getDisplayName (java.time.ZoneId/of tz-str)
                                      java.time.format.TextStyle/SHORT
                                      java.util.Locale/US)]
      (#{"ET" "EST" "EDT"} short-name))
    (catch Exception e)))

(s/def :google-json/time-zone eastern?)
(s/def :google-json/zoned-date-time (s/keys :req-un [:google-json/date-time :google-json/time-zone]))

(s/def :google-json/summary string?)
(s/def :google-json/start :google-json/zoned-date-time)
(s/def :google-json/end :google-json/zoned-date-time)

(s/def :google-json-full-day/date ->date)
(s/def :google-json-full-day/start (s/keys :req-un [:google-json-full-day/date]))
(s/def :google-json-full-day/end (s/keys :req-un [:google-json-full-day/date]))

(def full-day-event
  (s/keys :req-un [:google-json-full-day/start :google-json-full-day/end
                   :google-json/summary]))

(s/def ::event
  (s/or :date-time (s/keys :req-un [:google-json/end :google-json/start
                                    :google-json/summary])
        :full-day full-day-event))

(s/def ::events (s/coll-of ::event))

(defn- resp-error? 
  [{:keys [error body]}]
  (or error (:error body)))

(defn insert-event
  ([calendar-id token event]
   (let [result (a/promise-chan)]
     (insert-event calendar-id token event
                   {:attempts 1
                    :result result
                    :wait-ms 1000})
     result))
  ([calendar-id token event {:keys [attempts result wait-ms] :as retry-opts}]
   (-> base-api
       (str "calendars/" (client/url-encode calendar-id) "/events")
       (client/post (-> (json token) (assoc :body (serialize-json event)))
                    (fn [resp']
                      (let [resp (update resp' :body parse-json)]
                        (a/go 
                          (if (and (resp-error? resp) (< attempts 10))
                            (do
                              (println "Error saving event, retry attempt " attempts)
                              (a/<! (a/timeout wait-ms))
                              (insert-event calendar-id token event
                                            (-> retry-opts
                                                (update :attempts inc)
                                                (update :wait-ms * 2))))
                            (a/>! result resp)))))))
   result))

(defn insert-events
  [calendar-id token events]
  (s/assert ::oauth/req-auth-parts token)
  (s/assert ::events events)
  (->> events
       (mapv (partial insert-event calendar-id token))
       (map (comp :body a/<!!))))
