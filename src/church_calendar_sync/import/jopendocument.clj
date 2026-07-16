(ns church-calendar-sync.import.jopendocument
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::sheet #(instance? org.jopendocument.dom.spreadsheet.Sheet %))

(defn sheet-from-file [file]
  {:post [(s/assert ::sheet %)]}
  (-> file
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name (java.io.File.) (sheet-from-file)))

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