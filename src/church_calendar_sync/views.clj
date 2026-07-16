(ns church-calendar-sync.views
  (:require [church-calendar-sync.import :refer [ods-sheet->services]]
            [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]))

(def ^:const htmx-load
  [:script {:src "https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
            :integrity "sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
            :crossorigin "anonymous"}])

(def ^:const uploaded-file-name "file")

(defn ods-upload [upload-path]
  [:form {:action upload-path :method "post" :enctype "multipart/form-data"}
   "Select file to upload: "
   [:input {:type "file" :name uploaded-file-name}]
   [:p]
   [:input {:type "submit" :value "Upload"}]])

(defn main [upload-path]
  [:body
   [:div "Hello World"]
   (ods-upload upload-path)
   #_htmx-load])

;; this is copy-paste of `test-config`:
;; probably want to do some DI and sharing of some kind of file eventually?
(def ^:const import-sheet-config
  {:start-row 10
   :start-column 0
   :day-width 3
   :day-height 8
   :end-column 25
   :end-row 100})

(defn pstr [object]
  (with-out-str (clojure.pprint/pprint object)))

(defn processing-upload [{:keys [params] :as req}] 
  (->> (get params uploaded-file-name)
       (:tempfile)
       (sheet-from-file)
       (ods-sheet->services import-sheet-config)
       (pstr)
       (vector :body)))

(comment
(println {:foo "bar " :bar "foo" :yes "please" :no "thank you"})  
  )