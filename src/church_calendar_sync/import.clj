(ns church-calendar-sync.import 
  (:require
    [church-calendar-sync.import.calendar-grid :refer [group-days]]
    [church-calendar-sync.import.jopendocument :as ods]
    [church-calendar-sync.spec :as spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

;; (s/def :service/date-time inst?)
;;   this can be pulled out of every day
;; (s/def :service/type #{:liturgy :weekday-evening :vigil :moleben})
;;   can be pulled out of every day

;; (s/def :service/feast string?) ;; usually "saint", but more general
;;   always(??) there for liturgy, but need to "reach forward" for  for evening services
;; (s/def :service/all-english? boolean?)
;;   need to identify based on evening services (usually)

;; (s/def :service/name string?)
;;   this actually doesn't matter??
 
;; (s/def ::service
;;   (s/keys :req-un [:service/date-time
;;                    :service/name
;;                    :service/all-english?
;;                    :service/feast
;;                    :service/type]))


(s/def ::services (s/+ ::spec/service))

(defn- day-group->day-lines [day-width day-group]
  (->> day-group
       (partition day-width)
       (map (comp str/join :text))))

(defn- deduplicate-services [services]
  (->> services
       (group-by (juxt :service/date-time :service/type))
       (vals)
       (map (fn [dup-services]
              (apply merge-with #(if (nil? %1) %2 %1) dup-services)))))

(defn- day-groups->services [{:keys [day-width]} day-groups]
  (->> day-groups
       (map (partial day-group->day-lines day-width))
       (partition 2 1)
       (mapcat day-lines-pair->services)
       (deduplicate-services))) 

(defn ods-sheet->services [config sheet]
  (s/assert ::spec/config config)
  (s/assert ::ods/sheet sheet)
  (->> (ods/sheet->clj config sheet)
       (group-days config)
       (day-groups->services config)
       #_(s/assert ::services))) ; may not even need config, we'll see!

(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods")

  (def ^:const config
    {:start-row 10
     :start-column 0
     :day-width 3
     :day-height 8
     :end-column 25
     :end-row 100})

  config

  (s/check-asserts true)
  (def sheet (ods/sheet-from-file-path ods-file-name))

  (->> sheet
       (ods/sheet->clj config)
       (map #(dissoc % :java-cell))

       (group-days config)

       #_(map first)))