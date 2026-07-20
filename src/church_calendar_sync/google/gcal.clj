(ns church-calendar-sync.google.gcal
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [parse-json]]
   [clojure.spec.alpha :as s]
   [org.httpkit.client :as client]
   [church-calendar-sync.google.oauth.storage :as storage])
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

(defn calendars [token]
  (s/assert ::oauth/req-auth-parts token)
  (-> (str base-api "users/me/calendarList")
      (client/get (json token))
      read-resp))

(defn events
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

(def primary-events (partial events "primary"))


(comment
  (def date-range {:start-date (java.time.LocalDateTime/of 2026 6 1 0 0)
                   :end-date (java.time.LocalDateTime/of 2026 6 30  0 0)})

  (def token (church-calendar-sync.google.oauth.storage/get-token church-calendar-sync.core/storage-atom))

  token


  (calendars @oauth/res)

  (events "en.usa#holiday@group.v.calendar.google.com" date-range @oauth/res)

  (primary-events date-range @oauth/res)

  )
