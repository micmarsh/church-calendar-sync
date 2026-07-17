(ns church-calendar-sync.app
  (:require [church-calendar-sync.google.oauth :as oauth]
            [church-calendar-sync.google.oauth.storage :as storage]
            [church-calendar-sync.config-storage :as config]
            [church-calendar-sync.import :refer [ods-sheet->services]]
            [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
            [clojure.spec.alpha :as s]
            [ring.util.response :as response]))

(def ^:const htmx-load
  [:script {:src "https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
            :integrity "sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
            :crossorigin "anonymous"}])

(def ^:const uploaded-file-name "file")

(def ^:const main-view-path "/main")

(def ^:const upload-view-path "/ods-upload")

(def ods-upload
  [:form {:action upload-view-path :method "post" :enctype "multipart/form-data"}
   "Select file to upload: "
   [:input {:type "file" :name uploaded-file-name}]
   [:p]
   [:input {:type "submit" :value "Upload"}]])

(defn- google-login [ctx]
  (s/assert ::context ctx)
  (if-let [auth (storage/get-token (:token-storage ctx))]
    [:div
     [:h3 "Logged in to Google"]
     (str auth)]
    [:div [:a {:href (oauth/get-raw-oath-url ctx)} "Log in to Google"]]))

(s/def ::token-storage #(satisfies? storage/TokenStorage %))
(s/def ::config-storage #(satisfies? config/ConfigStorage %))
(s/def ::context (s/merge (s/keys :req-un [::token-storage ::config-storage]) ::oauth/creds))

(def ^:const select-calendar-path  "/select-calendar")

(defn- current-calendar [{:keys [config-storage] :as ctx}]
  (if-let [calendar-id (config/get-config config-storage ::current-calendar)]
    [:div "Currently will sync to calendar " calendar-id] ;; todo maybe should store readable name and id, display readable name? we'll see
    [:div [:a {:href select-calendar-path} "Select a calendar to sync to"]]))

(defn select-calendar [ctx]
  (s/assert ::context ctx)
  )

(defn main [context]
  (s/assert ::context context)
  [:body
   [:h1 "Calendar Sync"]
   ods-upload
   (google-login context)
   (current-calendar context)
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

(defn- assoc-expires-time [{:keys [expires-in] :as token-result}]
  (assoc token-result :expires (.plusSeconds (java.time.LocalDateTime/now) expires-in)))

(defn oauth-get-token [{:keys [token-storage] :as context} req]
  (s/assert ::context context)
  (let [code (oauth/ring-req->oauth-code req)
        token-result (oauth/oauth-token code context)]
    (storage/put-token! token-storage (assoc-expires-time token-result)))
  (response/redirect main-view-path))

(comment
  (.plusSeconds (java.time.LocalDateTime/now) 3600)

 (def members (:members *1))

 members 
 (map :name members)
  )