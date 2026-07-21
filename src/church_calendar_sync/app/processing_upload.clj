(ns church-calendar-sync.app.processing-upload 
  (:require
    [church-calendar-sync.config-storage :as config]
    [church-calendar-sync.google.gcal :as gcal]
    [church-calendar-sync.google.oauth.storage :as storage]
    [church-calendar-sync.import :refer [ods-sheet->services]]
    [church-calendar-sync.import :as import]
    [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
    [church-calendar-sync.spec :as spec]
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

(defn ->date-time [input]
  (try
    (java.time.ZonedDateTime/parse input)
    (catch Exception e)))

(defn ->date [input]
  (try
    (java.time.LocalDate/parse input)
    (catch Exception e)))

(s/def :google-json/date-time ->date-time)
(s/def :google-json/time-zone #{"America/New_York" "America/Chicago"})
(s/def :google-json/zoned-date-time (s/keys :req-un [:google-json/date-time :google-json/time-zone]))

(s/def :google-json/summary string?)
(s/def :google-json/start :google-json/zoned-date-time)
(s/def :google-json/end :google-json/zoned-date-time)

(s/def :google-json-full-day/start ->date)
(s/def :google-json-full-day/end ->date)

(def ^:private full-day-event
  (s/keys :req-un [:google-json-full-day/start :google-json-full-day/end
                   :google-json/summary]))

(s/def :google-json/event 
  (s/or :date-time (s/keys :req-un [:google-json/end :google-json/start 
                                    :google-json/summary])
        :full-day full-day-event))

(s/def ::events (s/coll-of :google-json/event))

(defn gcal-event-index [events]
  (s/assert ::events events)
  (group-by (comp #(.toLocalDate %) #(java.time.ZonedDateTime/parse %) :date-time :start) events))

(def service-type->name 
  (into {} (map (fn [[k v]] [v k]) import/service-type-map)))

(defn- overlapping-words [str1 str2]
  (some (into #{} (map str/lower-case) (str/split str1 #" ")) 
        (map str/lower-case (str/split str2 #" "))))

(overlapping-words "Sunday Divine Liturgy ~ Воскресная Божественная Литургия" "Sunday ?? After Pentecost")

(defn desc-matches? [gcal-json service]
  (s/assert :google-json/event gcal-json)
  (s/assert ::spec/service service)
  (some (partial overlapping-words (:summary gcal-json))
        [(:service/feast service "") (:event/description service "")
         (service-type->name (:service/type service :service-type/unknown))]))

(defn matches? [service gcal-json]
  (s/assert ::spec/service service)
  (s/assert :google-json/event gcal-json)
  (when-let [date-time-str (-> gcal-json :start :date-time)]
    (and (= (.toLocalDateTime (->date-time date-time-str)) (:event/date-time service))
         (desc-matches? gcal-json service))))

(def service-lengths
  {:service-type/liturgy (Duration/ofHours 2)
   :service-type/hours (Duration/ofMinutes 30)
   :service-type/moleben (Duration/ofHours 1)
   :service-type/vigil (Duration/ofHours 3)
   :service-type/weekday-evening (Duration/ofHours 2)})
;; filter out unknowns/confession events before we even get here?

(def default-tz (java.time.ZoneId/of "America/New_York"))

(defn- ->gcal-json-event [service]
  (let [start-time (:event/date-time service)
        type (:service/type service)]
    {:start {:date-time (gcal/local-dt->rfc3339 start-time)
             :time-zone (.getId default-tz)} ;; what to do about this? config DI? constant somewhere?
     :end {:date-time (gcal/local-dt->rfc3339 (.plus start-time (service-lengths type))) 
           :time-zone (.getId default-tz)}
     :summary (service-type->name type)}))

(defn- needs-feast? [service day-bucket]
  (and (= :service-type/liturgy (:service/type service))
       (empty? (filter #(and (s/valid? full-day-event %) (desc-matches? % service)) day-bucket))))

(defn service->gcal-events [existing-events service]
  (s/assert (s/map-of #(instance? java.time.LocalDate %) ::events) existing-events) 
  (s/assert ::spec/service service)
  (let [day (.toLocalDate (:event/date-time service))
        day-bucket (get existing-events day)
        exists? (some (partial matches? service) day-bucket)]
    (filter identity 
            [(when-not exists? (->gcal-json-event service))
             (when (needs-feast? service day-bucket) ;; check if service is liturgy and there's no all-day feast event yet
               {:start {:date (.format day java.time.format.DateTimeFormatter/ISO_DATE)}
                :end {:date (.format day java.time.format.DateTimeFormatter/ISO_DATE)}
                :summary (:service/feast service)})])))

(defn- add-events [calendar-id auth gcal-events]
  (throw (Exception. "TODO: THIS")))

(defn- sync-calendars [{:keys [token-storage config-storage] :as ctx} services] 
  (s/assert (s/coll-of ::spec/service) services)
  (let [auth (storage/get-token token-storage)
        calendar-id (:id (config/get-config config-storage :church-calendar-sync.app/current-calendar))
        date-range (services-range services)
        existing-events (-> (gcal/events calendar-id date-range auth) :body :items gcal-event-index)] 
    (->> services
         (mapcat (partial service->gcal-events existing-events))
         (add-events calendar-id auth))))

(defn run [ctx {:keys [params] :as req}]
  (s/assert ::spec/req-ctx ctx)
  (->> (get params uploaded-file-name)
       (:tempfile)
       (sheet-from-file)
       (ods-sheet->services import-sheet-config)
       (sync-calendars ctx)
       (pstr)
       (vector :body)))
