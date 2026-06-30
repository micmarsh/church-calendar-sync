(ns church-calendar-sync.jopendocument
  (:require
   [church-calendar-sync.spec :as spec]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::sheet #(instance? org.jopendocument.dom.spreadsheet.Sheet %))

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name
      (java.io.File.)
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn- cell->text [^org.jopendocument.dom.spreadsheet.Cell cell]
  ;; running into issue (with solution) described here https://stackoverflow.com/a/74628786
  (.getValue (.getElement cell)))

(defn sheet->clj
  [config ^org.jopendocument.dom.spreadsheet.Sheet sheet]
  (let [row-count (.getRowCount sheet)
        column-count (.getColumnCount sheet)]
    (for [row-i (range (get config :start-row 0) (get config :end-row row-count))
          column-i (range (get config :start-column 0) (get config :end-column column-count))
          :let [java-cell (.getImmutableCellAt sheet column-i row-i)]]
      {:column column-i
       :row row-i
       :text (cell->text java-cell)
       :java-cell java-cell})))

(defn- day-start-coords
  [{:keys [start-row start-column, day-width day-height]}
   {:keys [row column]}]
  (let [adjusted-row (- row start-row)
        adjusted-column (- column start-column)]
    [(- adjusted-row (mod adjusted-row day-height))
     (- adjusted-column (mod adjusted-column day-width))]))

(def ^:private only-group-vals
  (comp vals (partial sort-by key)))

(def ^:private months 
  ["Jan", "Feb", "March", "April", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"])
(def ^:private years (mapv str (range 2026 2070)))
(def ^:private day-of-month? (into #{} (map str (range 1 32))))

(defn- day-str-parts [str]
  (str/split str #" |'|,"))

(defn- contains-day-of-month? [str]
  (->> str
       (day-str-parts)
       (into #{})
       (set/intersection day-of-month?)
       (not-empty)))

(defn- contains-year? [str]
  (some (partial str/includes? str) years))

(defn- contains-month [str]
  (some (partial str/includes? str) months))

(defn- full-day-str? [str]
  (and (contains-month str)
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

(day-val [{:text "'1 Aug, 2026"}])

(defn keep-first-continuous [day-groups]
  (->> day-groups
       (partition 2 1)
       (take-while (fn [[current-day-group next-day-group]]
                     (consecutive? (day-val current-day-group) (day-val next-day-group))))
       ((fn [consecutives]
          (cons (ffirst consecutives) (map second consecutives))))))

(defn group-days [config cell-maps]
  (s/assert ::spec/config config)
  (s/assert (s/+ ::spec/cell) cell-maps)
  (->> cell-maps
       (group-by (partial day-start-coords config))
       (only-group-vals)
       (filter day-group?)
       (keep-first-continuous)))

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
  (def sheet (sheet-from-file-path ods-file-name))

  (->> sheet
       (sheet->clj config)
       (map #(dissoc % :java-cell))

       (group-days config)

       #_(map first)) 

  (def days-groups
    (->> sheet (ods/sheet->clj config) (ods/group-days {})))

  (map #(dissoc % :java-cell) (days-groups [0 9]))
  )