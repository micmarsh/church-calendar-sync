(ns church-calendar-sync.import 
  (:require
    [church-calendar-sync.import.jopendocument :as ods]
    [church-calendar-sync.spec :as spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [church-calendar-sync.import.calendar-grid :as grid]))

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

(def ^:private not-nil
  #(if (nil? %1) %2 %1))

(defn- merge-isolated-services [services]
  (s/assert (s/+ ::isolated-service) services)
  (->> services
       (group-by (juxt :service/date-time :service/type))
       (vals)
       (map (fn [dup-services]
              (apply merge-with not-nil dup-services)))
       (s/assert (s/+ ::spec/service)))) ;; todo: remove this laster form once testing is over?

;; keep in mind these are strings!
(s/def :isolated-service/day grid/day-of-month?)
(s/def :isolated-service/year (s/or nil? (into #{} grid/years)))
(s/def :isolated-service/month (s/or nil? grid/contains-month?))

(s/def ::isolated-service
  :req [:isolated-service/day
        :isolated-service/month
        :isolated-service/year
        :service/type]
  ;; there will be many others, these are guranteed^
  )

(defn- day-lines->isolated-services [day-lines]
  )

(defn- day-groups->services [{:keys [day-width]} day-groups]
  (->> day-groups
       ;; todo need to account for month-year! Maybe here or slightly "higher level!"
       (map (partial day-group->day-lines day-width))
       (mapcat day-lines->isolated-services)
       (merge-isolated-services))) 

(defn ods-sheet->services [config sheet]
  (s/assert ::spec/config config)
  (s/assert ::ods/sheet sheet)
  (->> (ods/sheet->clj config sheet)
       (grid/group-days config)
       (day-groups->services config)
       (s/assert ::services))) ; may not even need config, we'll see!

(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods")

  (def ^:const config
    {:start-row 10
     :start-column 0
     :day-width 3
     :day-height 8
     :end-column 25
     :end-row 100}) 

  (s/check-asserts true)
  (def sheet (ods/sheet-from-file-path ods-file-name))

  (->> sheet
       (ods/sheet->clj config)
       (map #(dissoc % :java-cell))

       (group-days config)

       #_(map first))
  
  )