(ns church-calendar-sync.jopendocument)

(defn sheet-from-file-path [ods-file-name]
  (-> ods-file-name
      (java.io.File.)
      (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
      (.getFirstSheet)))

(defn- cell->text [^org.jopendocument.dom.spreadsheet.Cell cell]
  ;; running into issue (with solution) described here https://stackoverflow.com/a/74628786
  (.getValue (.getElement cell)))

(defn- iter-cells [^org.jopendocument.dom.spreadsheet.Sheet sheet]
  (let [row-count (.getRowCount sheet)
        column-count (.getColumnCount sheet)]
    (for [row-i (range row-count)
          column-i (range column-count)]
      {:cell (.getImmutableCellAt sheet column-i row-i)
       :column column-i
       :row row-i})))

(defn- assoc-cell-text [cell-map]
  (assoc cell-map :text (-> cell-map :cell cell->text)))

(defn sheet->clj [^org.jopendocument.dom.spreadsheet.Sheet sheet]
  (->> sheet
       iter-cells
       (map assoc-cell-text)))
