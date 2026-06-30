(ns church-calendar-sync.jopendocument
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [church-calendar-sync.spec]))

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name
      (java.io.File.)
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn- cell->text [^org.jopendocument.dom.spreadsheet.Cell cell]
  ;; running into issue (with solution) described here https://stackoverflow.com/a/74628786
  (.getValue (.getElement cell)))

(defn sheet->clj
  ([sheet] (sheet->clj {} sheet))
  ([config ^org.jopendocument.dom.spreadsheet.Sheet sheet]
   (let [row-count (.getRowCount sheet)
         column-count (.getColumnCount sheet)]
     (for [row-i (range (get config :start-row 0) (get config :end-row row-count))
           column-i (range (get config :start-column 0) (get config :end-column column-count))
           :let [java-cell (.getImmutableCellAt sheet column-i row-i)]]
       {:column column-i
        :row row-i
        :text (cell->text java-cell)
        :java-cell java-cell}))))

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

(s/def :config/start-row :grid/row)
(s/def :config/start-column :grid/column)
(s/def :config/end-row :grid/row)
(s/def :config/end-column :grid/column)

(s/def ::config 
  (s/keys :req-un [:config/start-row :config/end-row :config/start-column :config/end-column]))

(s/def :cell/row :grid/row)
(s/def :cell/column :grid/column)
(s/def :cell/text string?)

(s/def ::cell (s/keys :req-un [:cell/column :cell/row :cell/text]))


(defn- day-start-coords
  ;; todo this is good motivator: want something that can destructure and run spec at same time?
  ;; maybe you're still just thinking too "typed"?
  [{:keys [start-row start-column, day-width day-height] :as config}
   {:keys [row column] :as cell}]
  (s/assert ::config config)
  (s/assert (s/keys :req-un [:cell/column :cell/row]) cell)
  (let [adjusted-row (- row start-row)
        adjusted-column (- column start-column)]
    [(- adjusted-row (mod adjusted-row day-height))
     (- adjusted-column (mod adjusted-column day-width))]))

(defn group-days [config cell-maps] 
  (s/assert ::config config)
  (s/assert (s/every :cell) cell-maps)
  (->> cell-maps
       (group-by (partial day-start-coords config))
       #_(filter day-group?)
       #_(keep-first-continuous)))

(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods") 

  (def sheet (sheet-from-file-path ods-file-name))

  (def days-groups 
    (->> sheet (sheet->clj config) (group-days {})))

  (map #(dissoc % :java-cell) (days-groups [0 9])) 
  )