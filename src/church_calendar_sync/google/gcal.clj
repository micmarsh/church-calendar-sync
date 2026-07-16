(ns church-calendar-sync.google.gcal
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [parse-json]]
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

(defn events
  [calendar-id
   {:keys [start-date end-date] :as params}
   {:keys [access-token token-type] :as token}]
  (s/assert ::date-range params)
  (s/assert ::oauth/token-result token)
  (-> (str base-api "calendars/" calendar-id "/events") 
      (client/get 
       {:headers {"Authorization" (str token-type " " access-token)}
        :content-type :json
        :accept :json
        :query-params {"timeMin" (local-dt->rfc3339 start-date)
                       "timeMax" (local-dt->rfc3339 end-date)}})
      (deref)
      :body
      parse-json))

(def primary-events (partial events "primary"))


(comment 
  (def date-range {:start-date (java.time.LocalDateTime/of 2026 6 1 0 0 )
                   :end-date (java.time.LocalDateTime/of 2026 6 30  0 0 )}) 

  (primary-events date-range @oauth/res)
  
  (def events-resp *1) 

  )
