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