(ns church-calendar-sync.google.gcal
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [parse-json serialize-json]]
   [clojure.spec.alpha :as s]
   [org.httpkit.client :as client])
  (:import
   [java.time ZoneId]))

(s/def ::start-date ::spec/date-time)
(s/def ::end-date ::spec/date-time)
(s/def ::date-range (s/keys :req-un [::start-date ::end-date]))

(def ^:private timezone
  (ZoneId/of "America/Detroit"))

(defn local-dt->rfc3339 [local-date-time]
  (-> local-date-time
      (.atZone timezone)
      (.format java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME)))

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

(defn get-events
  [calendar-id
   {:keys [start-date end-date] :as params}
   token]
  (s/assert ::date-range params)
  (s/assert ::oauth/req-auth-parts token)
  (-> (str base-api "calendars/" (client/url-encode calendar-id) "/events")
      (client/get (-> (json token)
                      (assoc :query-params {"timeMin" (local-dt->rfc3339 start-date)
                                            "timeMax" (local-dt->rfc3339 end-date)})))
      read-resp))


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

(defn insert-event [calendar-id token event]
  (-> base-api
      (str "calendars/" (client/url-encode calendar-id) "/events")
      (client/post (-> (json token) (assoc :body (serialize-json event))))))

(defn insert-events
  [calendar-id token events]
  (s/assert ::oauth/req-auth-parts token)
  (s/assert ::events events)
  ;; (clojure.pprint/pprint calendar-id)
  ;; (clojure.pprint/pprint token)
  ;; (clojure.pprint/pprint events)
  (->> events
       (mapv (partial insert-event calendar-id token))
       (map (comp :body read-resp))))

(comment
  (def calendar-id "78c12c8508d6ebb0b919960e6300c1898c1c8f7594d160d47836bf03854db066@group.calendar.google.com")

  (def token {:access-token
              "ya29.a0ARGnu0Y0CgqReb11bM4whWUUDMjLD0cVaXjzmpxQv3TJcNjnk0mxZV4C65lTGzqQ6Io2UC61tauGADwxj6sRGVHAAF3fHBm4MmyjBPPuho1R43i1EL5ZQwxLqRIDBzl5YiOh7Y1i1vOrh1OHu9vyqvZVvYrvt-BxM6gp_NUjX_25e3s9Y_-oxoVzA8rJLxLkehClf6A_3TPVIDW1iYoG6pdEpunZ-Zxky7s7KiRUhF1ldWdSVgunQFz7UxpVtfErM64fNAyK89vEuJW7GRZolefL24X3aCgYKAScSARESFQHGX2MiNXC2RFoDVj69EiK51qsVgA0291",
              :expires-in 3599,
              :scope "https://www.googleapis.com/auth/calendar",
              :token-type "Bearer",
              :expires #time/date-time "2026-07-22T13:53:09.750924919"})

  (def inputs '({:start
                 {:date-time "2026-06-01T08:00:00-04:00",
                  :time-zone "America/New_York"},
                 :end
                 {:date-time "2026-06-01T10:00:00-04:00",
                  :time-zone "America/New_York"},
                 :summary "Div. Liturgy"}
                {:start {:date "2026-06-01"},
                 :end {:date "2026-06-01"},
                 :summary "Holy Spirit Day"}
                {:start
                 {:date-time "2026-06-02T18:00:00-04:00",
                  :time-zone "America/New_York"},
                 :end
                 {:date-time "2026-06-02T20:00:00-04:00",
                  :time-zone "America/New_York"},
                 :summary "Evening Services"}
                {:start
                 {:date-time "2026-06-03T09:00:00-04:00",
                  :time-zone "America/New_York"},
                 :end
                 {:date-time "2026-06-03T11:00:00-04:00",
                  :time-zone "America/New_York"},
                 :summary "Div. Liturgy"}
                {:start {:date "2026-06-03"},
                 :end {:date "2026-06-03"},
                 :summary "Sts. Constantine and Helen"}))

  (insert-event calendar-id token (first inputs))
@*1

  )

