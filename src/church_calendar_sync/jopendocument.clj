(ns church-calendar-sync.jopendocument)


(def ^:const relevant-columns 25)

(def ^:const relevant-rows 100)

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name
      (java.io.File.)
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn- cell->text [^org.jopendocument.dom.spreadsheet.Cell cell]
  ;; running into issue (with solution) described here https://stackoverflow.com/a/74628786
  (.getValue (.getElement cell)))

;; sheet -> lazy seq of lazy seqs
(defn sheet->clj [^org.jopendocument.dom.spreadsheet.Sheet sheet]
  (let [row-count (.getRowCount sheet)
        column-count (.getColumnCount sheet)]
    (->> (range row-count)
         (map (fn [row-i]
                (->> (range column-count)
                     (map (fn [column-i]
                            (let [java-cell (.getImmutableCellAt sheet column-i row-i)]
                              {:column column-i
                               :row row-i
                               :text (cell->text java-cell)
                               :java-cell java-cell})))))))))

;; lazy seq of lazy seqs -> trimmed vector of trimmed vectors
(defn strict [cell-maps]
  (mapv #(into [] (take relevant-columns) %)
        (take relevant-rows cell-maps)))


(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods")

  (strict (sheet->clj (sheet-from-file-path ods-file-name)))
  )