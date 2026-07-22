(ns church-calendar-sync.app.processing-upload 
  (:require
    [church-calendar-sync.google.gcal :as gcal]
    [church-calendar-sync.google.oauth :as oauth]
    [church-calendar-sync.google.oauth.storage :as storage]
    [church-calendar-sync.import :refer [ods-sheet->services service-type-map]]
    [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
    [church-calendar-sync.spec :as spec]
    [church-calendar-sync.storage.config :as config]
    [church-calendar-sync.utils :refer [sort-by-date]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]) 
  (:import
    [java.time Duration]))

;; this is copy-paste of `test-config`:
;; probably want to do some DI and sharing of some kind of file eventually?
(def ^:const import-sheet-config
  {:start-row 10
   :start-column 0
   :day-width 3
   :day-height 8
   :end-column 25
   :end-row 100})

(def ^:const uploaded-file-name "file")

(defn pstr [object]
  (with-out-str (clojure.pprint/pprint object)))

(defn- services-range [services]
  (->> (sort-by-date :event/date-time services)
       ((juxt (comp :event/date-time first) (comp :event/date-time last)))
       ((fn [[start end]] {:start-date start :end-date end}))))


(defn gcal-event-index [events]
  (s/assert ::gcal/events events)
  (group-by (comp #(.toLocalDate %) #(java.time.ZonedDateTime/parse %) :date-time :start) events))

(def service-type->name 
  (into {} (map (fn [[k v]] [v k]) service-type-map)))

(defn- overlapping-words [str1 str2]
  (some (into #{} (map str/lower-case) (str/split str1 #" ")) 
        (map str/lower-case (str/split str2 #" "))))

(overlapping-words "Sunday Divine Liturgy ~ Воскресная Божественная Литургия" "Sunday ?? After Pentecost")

(defn desc-matches? [gcal-json service]
  (s/assert ::gcal/event gcal-json)
  (s/assert ::spec/service service)
  (some (partial overlapping-words (:summary gcal-json))
        [(:service/feast service "") (:event/description service "")
         (service-type->name (:service/type service :service-type/unknown))]))

(defn matches? [service gcal-json]
  (s/assert ::spec/service service)
  (s/assert ::gcal/event gcal-json)
  (when-let [date-time-str (-> gcal-json :start :date-time)]
    (and (= (.toLocalDateTime (gcal/->date-time date-time-str)) (:event/date-time service))
         (desc-matches? gcal-json service))))

(def service-lengths
  {:service-type/liturgy (Duration/ofHours 2)
   :service-type/hours (Duration/ofMinutes 30)
   :service-type/moleben (Duration/ofHours 1)
   :service-type/vigil (Duration/ofHours 3)
   :service-type/weekday-evening (Duration/ofHours 2)})
;; filter out unknowns/confession events before we even get here?

(defn- ->gcal-json-event [^java.time.ZoneId tz service]
  (let [start-time (:event/date-time service)
        type (:service/type service)]
    {:start {:date-time (gcal/local-dt->rfc3339 start-time)
             :time-zone (.getId tz)} ;; what to do about this? config DI? constant somewhere?
     :end {:date-time (gcal/local-dt->rfc3339 (.plus start-time (service-lengths type))) 
           :time-zone (.getId tz)}
     :summary (service-type->name type)}))

(defn- needs-feast? [service day-bucket]
  (and (= :service-type/liturgy (:service/type service))
       (empty? (filter #(and (s/valid? gcal/full-day-event %) (desc-matches? % service)) day-bucket))))

(defn service->gcal-events [tz existing-events service]
  (s/assert (s/map-of #(instance? java.time.LocalDate %) ::gcal/events) existing-events) 
  (s/assert ::spec/service service)
  (let [day (.toLocalDate (:event/date-time service))
        day-bucket (get existing-events day)
        exists? (some (partial matches? service) day-bucket)]
    (filter identity 
            [(when-not exists? (->gcal-json-event tz service))
             (when (needs-feast? service day-bucket) ;; check if service is liturgy and there's no all-day feast event yet
               {:start {:date (.format day java.time.format.DateTimeFormatter/ISO_DATE)}
                :end {:date (.format day java.time.format.DateTimeFormatter/ISO_DATE)}
                :summary (:service/feast service)})])))

(defn- add-events [calendar auth gcal-events]
  ;(s/assert map? calendar) todo real spec?
  (s/assert ::oauth/token-result auth)
  (s/assert ::gcal/events gcal-events)
  (gcal/insert-events (:id calendar) auth 
                      ;; TAKE 5 is for testing purposes
                      (take 5 gcal-events)))

(defn- prepare-add-events [calendar auth services]
  ;(s/assert string? calendar) todo real spec?
  (s/assert ::oauth/token-result auth)
  (let [date-range (services-range services)
        existing-events (->> (gcal/events (:id calendar) date-range auth)
                             :body :items
                             ;; these aren't relevant for filtering anyway
                             (filter (comp gcal/eastern? :time-zone))
                             gcal-event-index)
        tz (java.time.ZoneId/of (:time-zone calendar))]
    (mapcat (partial service->gcal-events tz existing-events) services)))

(defn- sync-calendars [{:keys [token-storage config-storage] :as ctx} services] 
  (s/assert (s/coll-of ::spec/service) services)
  (let [auth (storage/get-token token-storage)
        calendar (config/get-config config-storage :church-calendar-sync.app/current-calendar)] 
    (->> services
         (filter (comp service-lengths :service/type)) ;; todo
         (prepare-add-events calendar auth)
         (add-events calendar auth))))

(defn run [ctx {:keys [params] :as req}]
  (s/assert ::spec/req-ctx ctx)
  (->> (get params uploaded-file-name)
       (:tempfile)
       (sheet-from-file)
       (ods-sheet->services import-sheet-config)
       (sync-calendars ctx)
       (pstr)
       (vector :body)))
