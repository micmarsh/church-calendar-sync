(ns church-calendar-sync.import 
  (:require
    [church-calendar-sync.import.calendar-grid :refer [group-days]]
    [church-calendar-sync.import.jopendocument :as ods]
    [church-calendar-sync.spec :as spec]
    [clojure.spec.alpha :as s]))

(s/def ::services (s/+ ::spec/service))

(defn- day-groups->services [config day-groups]
  day-groups) ;; todo this! very wrong spec lol

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