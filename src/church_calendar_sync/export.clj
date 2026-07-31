(ns church-calendar-sync.export
  (:import
   [org.jopendocument.model OpenDocument]
   [com.lowagie.text Document])
  (:require
   [clojure.java.io :as io]))

;; todo all of this in a giant `let` in some function? May be easier to resuse later

(defn test []
  (let [doc (OpenDocument.)
        _ (.loadFrom doc (str (System/getProperty "user.home") "/Documents/FrGregoryCalendarjune2026.ods"))
        document (Document. com.lowagie.text.PageSize/A4)
        out-file (io/file "output.pdf")
        pdf (com.lowagie.text.pdf.PdfDocument.)
        _ (.addDocListener document pdf)
        file-output-stream (io/output-stream out-file)
        writer (com.lowagie.text.pdf.PdfWriter/getInstance pdf file-output-stream)
        _ (.addWriter pdf writer)
        _ (.open document)
        page-size (.getPageSize document)
        w (int (* (.getWidth page-size) 0.9))
        h (int (* (.getHeight page-size) 0.95))
        cb (.getDirectContent writer)
        tp (.createTemplate cb w h)
        g2 (.createPrinterGraphics tp w h nil)
        
        _ (doto tp (.setWidth w) (.setHeight h))
        
        renderer (org.jopendocument.renderer.ODTRenderer. doc)
        _ (doto renderer (.setIgnoreMargins true) (.setPaintMaxResolution true)
                (.setResizeFactor (/ (.getPrintWidth renderer) 2))
                (.paintComponent g2))

        _ (.dispose g2) 
        
        offset-x (/ (- (.getWidth page-size) w) 2)
        offset-y (/ (- (.getHeight page-size) h) 2)
        _ (.addTemplate cb tp offset-x offset-y)

        _ (.close document)
        ]) 
  )

(test)

