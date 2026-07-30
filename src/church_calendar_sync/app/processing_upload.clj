(ns church-calendar-sync.app.processing-upload
  (:require
   [church-calendar-sync.google.gcal :as gcal]
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.import :refer [ods-sheet->services service-type-map]]
   [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.storage.impls :as impl]
   [church-calendar-sync.utils :refer [sort-by-date]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [ring.util.response :as response])
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
       ((fn [[start end]] {:start-date start :end-date (.plusDays end 1)}))))

(defn- event-day [{:keys [start]}]
  (let [{:keys [date-time date]} start]
    (cond
      date-time (-> date-time (java.time.ZonedDateTime/parse) (.toLocalDate))
      date (java.time.LocalDate/parse date))))

(defn gcal-event-index [events]
  (s/assert ::gcal/events events)
  (group-by event-day events))

(def service-type->name
  (into {} (map (fn [[k v]] [v k]) service-type-map)))

(defn- overlapping-words [str1 str2]
  (some (into #{} (map str/lower-case) (str/split str1 #" "))
        (map str/lower-case (str/split str2 #" "))))

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
    {:start {:date-time (gcal/->rfc3339 start-time)
             :time-zone (.getId tz)} ;; what to do about this? config DI? constant somewhere?
     :end {:date-time (gcal/->rfc3339 (.plus start-time (service-lengths type)))
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

(defn keep-for-deduplication?
  "Checks if time zone is either eastern or doesn't specify tz at all"
  [{:keys [start end] :as event}]
  (s/assert ::gcal/event event)
  (and (or (some-> start :time-zone gcal/eastern?) (:date start))
       (or (some-> end :time-zone gcal/eastern?) (:date end))))

(defn- prepare-add-events [ctx services] 
  (let [{:keys [auth calendar]} (impl/get-saved-settings ctx)
        date-range (services-range services)
        existing-events (gcal/get-events (:id calendar) date-range auth)
        existing-events-by-day (->> existing-events
                                    (filter keep-for-deduplication?)
                                    gcal-event-index)
        tz (java.time.ZoneId/of (:time-zone calendar))]
    {::services-from-file services
     ::events-from-calendar existing-events
     ::events-to-add (mapcat (partial service->gcal-events tz existing-events-by-day) services)}))

(s/def ::services-from-file (s/+ ::spec/service))
(s/def ::events-from-calendar ::gcal/events)
(s/def ::events-to-add ::gcal/events)
(s/def ::initial-ui-input (s/keys :req [::services-from-file ::events-from-calendar ::events-to-add]))


(s/def ::displayable (s/or ::service ::spec/service
                           ::gcal-event ::gcal/event))

(defmulti display #(let [result (s/conform ::displayable %)]
                     (if (= ::s/invalid result) result
                         (key result))))
(defmethod display ::service
  [s]
  (str (:event/date-time s) " " (:service/feast s (:event/description s)) " "  (name (:service/type s ""))))

(defn- event-time [{:keys [start]}]
  (let [{:keys [date-time date]} start]
    (cond
      date-time (-> date-time (str/split #"T") (last))
      date "00:00")))

(defmethod display ::gcal-event
  [event]
  (str (event-day event) "T" (event-time event) " " (:summary event)))

(defn- trim-display [str']
  (if (> (count str') 75) 
    (str (str/join (take 72 str')) "...")
    str'))

(defn display-all [items]
  (->> items
       (map display)
       (map trim-display)
       (sort)
       (map (partial vector :li))
       (vector :ul)))

(def ^:const sync-to-calendar-path "/sync-to-calendar")

(defn- add-events-hiccup
  [{::keys [services-from-file
            events-from-calendar
            events-to-add] :as input}]
  (s/assert ::initial-ui-input input)
  [:body
   [:form {:action sync-to-calendar-path :method "post"}
    [:div{ :style {:display "flex" :justify-content "space-around"}}
     [:div [:h3 "Services from File"] (display-all services-from-file)]
     [:div [:h3 "Events from Calendar"] (display-all events-from-calendar)]
     [:div [:h3 "Events to Add"] (display-all events-to-add)]] 
    [:input {:type "submit" :value "Sync"}]]])

(defonce events-to-add-cache (atom nil))

(defn run-initial [ctx {:keys [params] :as req}]
  (s/assert ::spec/req-ctx ctx)
  (->> (get params uploaded-file-name)
       (:tempfile)
       (sheet-from-file)
       (ods-sheet->services import-sheet-config)
       (filter (comp service-lengths :service/type)) ;; todo some other way of handling/reporting on unknown services 
       (prepare-add-events ctx)
       (reset! events-to-add-cache)
       (add-events-hiccup)))

(def ^:const redirect-after-s 10)

(defn sync-to-calendar [ctx req]
  (s/assert ::spec/req-ctx ctx)
  (let [{:keys [calendar auth]} (impl/get-saved-settings ctx)
        events-to-add (::events-to-add @events-to-add-cache)]
    (if (or (nil? calendar) (nil? auth) (nil? events-to-add))
      (response/redirect "/main")  ;; todo: move these to another ns? Another use case for custom messaging/redirect if needed as well
      (do
        (future (gcal/insert-events (:id calendar) auth events-to-add))
        [:body
         [:meta {:http-equiv "refresh" :content (str redirect-after-s ";url=/main")}] ;; todo consolidate main string OR need new location for "upload results"
         [:body [:h2 "Redirecting in " redirect-after-s " seconds"]]]))))
