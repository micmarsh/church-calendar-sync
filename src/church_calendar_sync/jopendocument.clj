(ns church-calendar-sync.jopendocument 
  (:require
    [clojure.string :as str]))

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name
      (java.io.File.)
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn- cell->text [^org.jopendocument.dom.spreadsheet.Cell cell]
  ;; running into issue (with solution) described here https://stackoverflow.com/a/74628786
  (.getValue (.getElement cell)))

;; sheet -> lazy seq of lazy seqs
;; todo just limit rows and columns in here? Maybe, need to actually plan/design how this is going down
(defn sheet->clj
  ([sheet] (sheet->clj {} sheet))
  ([config ^org.jopendocument.dom.spreadsheet.Sheet sheet]
   (for [:let [row-count (.getRowCount sheet)
               column-count (.getColumnCount sheet)]
         row-i (range (get config :start-row 0) (get config :end-row row-count))
         column-i (range (get config :start-column 0) (get config :end-column column-count))
         :let [java-cell (.getImmutableCellAt sheet column-i row-i)]]
     {:column column-i
      :row row-i
      :text (cell->text java-cell)
      :java-cell java-cell})))

;; from here down is much more specific to this app rather than jopendocument
;; probably needs separate ns

;; todo clojure spec! This will likely come from "user space"
(def ^:const config 
  {:start-row 10
   :start-column 0 
   :day-width 3
   :day-height 8
   :end-column 25
   :end-row 100})

(defn- day-start-coords
  [{:keys [start-row start-column, day-width day-height]}
   {:keys [row column]}]
  (let [adjusted-row (- row start-row)
        adjusted-column (- column start-column)]
    [(- adjusted-row (mod adjusted-row day-height))
     (- adjusted-column (mod adjusted-column day-width))]))

(defn group-days [config cell-maps]
  (->> cell-maps
       (group-by (partial day-start-coords config))
       #_(filter day-group?)
       #_(keep-first-continuous)))

(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods") 

  (def sheet (sheet-from-file-path ods-file-name))

  (def days-groups 
    (->> sheet (sheet->clj config) (group-days config)))

  (map #(dissoc % :java-cell) (days-groups [0 9])) 
  )


(def ^:const relevant-columns 25)

(def ^:const relevant-rows 100)


;; lazy seq of lazy seqs -> trimmed vector of trimmed vectors
(defn strict [cell-maps]
  (mapv #(into [] (take relevant-columns) %)
        (take relevant-rows cell-maps)))

(defn- inc-grid [[row column] grid]
  (let [current-row (get grid row)
        next-column (inc column)]
    (if (>= next-column (count current-row))
      [(inc row) 0]
      [row next-column])))

(def months
  ["Jan", "Feb", "March", "April", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"])
(def years (mapv str (range 2026 2070)))
(def days (mapv str (range 1 32)))

(defn- start-cell? [{:keys [text]}]
  (let [words (into #{} (str/split text #" "))]
    (and (some words months)
         (some words days)
         (some words years))))

(defn starting-day [cell-map-vectors]
  (loop [coords [0 0]]
    (let [cell (get-in cell-map-vectors coords)]
      (if (start-cell? cell)
        cell
        ;; this is wrong: inc row and column needs to just be some kind of incrementing the right 
        ;; coordinate in a pair based on the grid? (inc-coord [x y] grid) 
        ;; yeah, that makes sense  
        (recur (inc-grid coords cell-map-vectors))))))
