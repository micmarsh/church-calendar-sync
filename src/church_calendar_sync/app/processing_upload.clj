(ns church-calendar-sync.app.processing-upload 
  (:require
    [church-calendar-sync.config-storage :as config]
    [church-calendar-sync.google.oauth.storage :as storage]
    [church-calendar-sync.import :refer [ods-sheet->services]]
    [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
    [church-calendar-sync.spec :as spec]
    [church-calendar-sync.utils :refer [sort-by-date]]
    [clojure.spec.alpha :as s]
    [church-calendar-sync.google.gcal :as gcal]))

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

(defn- sync-calendars [{:keys [token-storage config-storage] :as ctx} services] 
  (s/assert (s/coll-of ::spec/service) services)
  (let [auth (storage/get-token token-storage)
        calendar-id (:id (config/get-config config-storage :church-calendar-sync.app/current-calendar))
        date-range (services-range services)
        existing-events (gcal/events calendar-id date-range auth)]
    (->> services
         (filter-services existing-events)
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
