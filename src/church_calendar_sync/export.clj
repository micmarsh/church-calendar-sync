(ns church-calendar-sync.export
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str])
  (:import
   [com.lowagie.text Document]
   [javax.swing JFrame]
   [org.jopendocument.model OpenDocument]
   [org.jopendocument.panel ODSViewerPanel]))

(def ^:const out-dir "/tmp/")

(defn invoke-libre-office [ods-file-path]
  (let [parts (str/split ods-file-path #"/")]
    (shell/sh "libreoffice" "--headless" "--convert-to" "pdf" ods-file-path "--outdir" out-dir)
    (str out-dir (first (str/split (last parts) #"\.")) ".pdf")))

;; return bytes/file path (probably one line difference)
(defn export-to-pdf [ods-file-path] ;; can probably extract this out of tempfile!
  (->> ods-file-path
       (invoke-libre-office)
       (io/as-file)))

;; TODO probably delete this entirely soon
(comment 
  (def pp' clojure.pprint/pprint)
  

  (defn find-font [obj]
    (-> obj
        (clojure.reflect/reflect)
        (update :members (fn [members] (filter #(str/includes? (-> % :name str str/lower-case)  "font") members)))))
  

  (find-font (OpenDocument.))
  

  (defn test-view []
    (let [doc (OpenDocument.)
          _ (.loadFrom doc (str (System/getProperty "user.home") "/Documents/FrGregoryCalendarjune2026.ods"))
          ;; _ (.loadFrom doc (str (System/getProperty "user.home") "/Documents/invoice_should_work_to_pdf.ods"))
          f (JFrame. "Viewer")
          viewer (ODSViewerPanel. doc)
          _ (doto f (.setContentPane viewer) (.pack)
                  (.setVisible true))])
    

    (test-view))
  

  (defn test []
    (let [doc (OpenDocument.)
          ;;        _ (.loadFrom doc (str (System/getProperty "user.home") "/Documents/FrGregoryCalendarjune2026.ods")) 
          _ (.loadFrom doc (str (System/getProperty "user.home") "/Documents/invoice_should_work_to_pdf.ods"))
          document (Document. com.lowagie.text.PageSize/A4)
          out-file (io/file "output.pdf")
          pdf (com.lowagie.text.pdf.PdfDocument.)
          _ (.addDocListener document pdf)

          _ (pp' (find-font document))
          _ (pp' (find-font pdf))

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

          _ (pp' (find-font tp))

          _ (pp' (clojure.reflect/reflect cb))

          _ (pp' (find-font g2))
          
          _ (doto tp (.setWidth w) (.setHeight h))

          renderer (org.jopendocument.renderer.ODTRenderer. doc)
          _ (doto renderer (.setIgnoreMargins true) (.setPaintMaxResolution true)
                  (.setResizeFactor (/ (.getPrintWidth renderer) 2)) 
                  (.paintComponent g2))

          _ (.dispose g2)

          offset-x (/ (- (.getWidth page-size) w) 2)
          offset-y (/ (- (.getHeight page-size) h) 2)
          _ (.addTemplate cb tp offset-x offset-y)

          _ (.close document)]) 
    ) 

  (test)
  )

