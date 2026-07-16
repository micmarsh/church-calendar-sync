(ns church-calendar-sync.import.calendar-grid 
  (:require
    [church-calendar-sync.spec :as spec]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

(defn- day-start-coords
  [{:keys [start-row start-column, day-width day-height]}
   {:keys [row column]}]
  (let [adjusted-row (- row start-row)
        adjusted-column (- column start-column)]
    [(- adjusted-row (mod adjusted-row day-height))
     (- adjusted-column (mod adjusted-column day-width))]))

(def ^:private only-group-vals
  (comp vals (partial sort-by key)))

(def months
  ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])
(def years (mapv str (range 2026 2070)))
(def day-of-month? (into #{} (map str (range 1 32))))

(defn- day-str-parts [str]
  (str/split str #" |'|,"))

(defn- contains-day-of-month? [str]
  (->> str
       (day-str-parts)
       (into #{})
       (set/intersection day-of-month?)
       (not-empty)))

(defn contains-year? [str]
  (some (partial str/includes? str) years))

(defn contains-month? [str]
  (some (partial str/includes? str) months))

(defn- full-day-str? [str]
  (and (contains-month? str)
       (contains-year? str)
       (contains-day-of-month? str)))

(defn- day-group? [group]
  (let [day-cell-val (:text (first group))]
    (or (full-day-str? day-cell-val)
        (day-of-month? day-cell-val))))

(defn consecutive? [current-day-val next-day-val]
  (or (= (inc current-day-val) next-day-val)
      (and (= 1 next-day-val)
           (#{28, 29, 30, 31} current-day-val))))

(defn- day-val [day-group]
  (->> (first day-group)
       (:text)
       (day-str-parts)
       (remove empty?)
       (first)
       (Integer.)))

(defn keep-first-continuous [day-groups]
  (->> day-groups
       (partition 2 1)
       (take-while (fn [[current-day-group next-day-group]]
                     (consecutive? (day-val current-day-group) (day-val next-day-group))))
       ((fn [consecutives]
          (cons (ffirst consecutives) (map second consecutives))))))

(s/def ::day-cell-groups
  (s/+ (s/and vector? (s/+ ::spec/cell))))

(defn group-days [config cell-maps]
  (s/assert ::spec/sheet-config config)
  (s/assert (s/+ ::spec/cell) cell-maps)
  (->> cell-maps
       (group-by (partial day-start-coords config))
       (only-group-vals)
       (filter day-group?)
       (keep-first-continuous)
       (s/assert ::day-cell-groups)))