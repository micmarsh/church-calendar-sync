(ns church-calendar-sync.views)

(def ^:const htmx-load
  [:script {:src "https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
            :integrity "sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
            :crossorigin "anonymous"}])

(defn ods-upload [upload-path]
  [:form {:action upload-path :method "post" :enctype "multipart/form-data"}
   "Select file to upload:"
   [:input {:type "file" :name "file"}]
   [:p]
   [:input {:type "submit" :value "Upload"}]]
  #_[:form {:hx-encoding "multipart/form-data" :hx-post upload-path
            :_ "on htmx:xhr:progress (loaded, total) set #progress.value to (loaded/total) *100"}
     [:input {:type "file" :name "file"}]
     [:button "Upload"]
     [:progress {:id "progress" :value 0 :max 100}]])

(defn main [upload-path]
  [:body
   [:div "Hello World"]
   (ods-upload upload-path)
   htmx-load])

(def last-upload-req (atom nil))

(defn processing-upload [req]
  (println req)
  (reset! last-upload-req 
          ;; this is not extracting actual file!?
          (let [input (clojure.java.io/input-stream (:body req))
                file (java.io.File. "tmp-23432134")
                output (clojure.java.io/output-stream file)]
            (.transferTo input output)
            file))
  [:body (str req)])

(comment
( -> @last-upload-req       (org.jopendocument.dom.spreadsheet.SpreadSheet/createFromFile)
                      (.getFirstSheet))
  )