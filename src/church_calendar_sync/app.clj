(ns church-calendar-sync.app
  (:require [church-calendar-sync.config-storage :as config]
            [church-calendar-sync.google.gcal :as gcal]
            [church-calendar-sync.google.oauth :as oauth]
            [church-calendar-sync.google.oauth.storage :as storage]
            [church-calendar-sync.import :refer [ods-sheet->services]]
            [church-calendar-sync.import.jopendocument :refer [sheet-from-file]]
            [clojure.spec.alpha :as s]
            [ring.util.response :as response]))

(def ^:const htmx-load
  [:script {:src "https://cdn.jsdelivr.net/npm/htmx.org@2.0.10/dist/htmx.min.js"
            :integrity "sha384-H5SrcfygHmAuTDZphMHqBJLc3FhssKjG7w/CeCpFReSfwBWDTKpkzPP8c+cLsK+V"
            :crossorigin "anonymous"}])

(def ^:const main-view-path "/main")

(def ^:private oauth-redirect (atom nil))
(defn set-oauth-redirect! [path] (reset! oauth-redirect path))
(defn get-oauth-redirect! []
  (let [result @oauth-redirect]
    (reset! oauth-redirect nil)
    (or result main-view-path)))

(def ^:const uploaded-file-name "file")

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
     [:div "FOR TESTING: google auth token creds"]
     (str auth)]
    [:div [:a {:href (oauth/get-raw-oath-url ctx)} "Log in to Google"]]))

(s/def ::token-storage #(satisfies? storage/TokenStorage %))
(s/def ::config-storage #(satisfies? config/ConfigStorage %))
(s/def ::context (s/merge (s/keys :req-un [::token-storage ::config-storage]) ::oauth/creds))

(def ^:const calendar-list-path  "/calendar-list")

(defn- current-calendar [{:keys [config-storage] :as ctx}]
  (if-let [{:keys [summary]} (config/get-config config-storage ::current-calendar)]
    [:div "Will sync to calendar \"" summary \"] ;; todo store readable name and id, display readable name? we'll see
    [:div [:a {:href calendar-list-path} "Select a calendar to sync to"]]))

(def ^:const select-calendar-path "/select-calendar")

(def ^:const select-calendar-param "calendar-selection")

(defonce calendar-cache (atom []))

(defn calendar-list [{:keys [token-storage] :as ctx}]
  (s/assert ::context ctx)
  (if-let [token-result (storage/get-token token-storage)]
    ;;todo something on 400 or 500? May want functions to throw and a unified middlware for all error types?
    ;;also there's generally a ton going on in this function in general 
    (let [calendars (->> (gcal/calendars token-result) :body :items (filter (comp #{"owner"} :access-role)))]
      (reset! calendar-cache calendars)
      [:body
       [:h2 "Select a Calendar to Sync to"]
       [:form {:action select-calendar-path :method "post"}
        (for [{:keys [id summary]} calendars
              node [[:input {:type "radio" :name select-calendar-param :value id :id id}]
                    [:label {:for id} summary]
                    [:br]]]
          node)
        [:input {:type "submit" :value "Submit"}]]])
    (do 
      (set-oauth-redirect! calendar-list-path)
      (response/redirect (oauth/get-raw-oath-url ctx)))))

#_(->> (gcal/calendars (storage/get-token church-calendar-sync.core/storage-atom))
       :body
       :items
       (take 3))

#_(swap! church-calendar-sync.core/storage-atom assoc :config nil)

(defn select-calendar [{:keys [config-storage] :as ctx} {:keys [params] :as req}]
  (s/assert ::context ctx)
  (let [calendar-id (get params select-calendar-param)]
    (if-let [calendar (->> @calendar-cache (filter (comp #{calendar-id} :id)) (first))]
      (do
        (config/put-config! config-storage ::current-calendar (select-keys calendar [:summary :id]))
        (response/redirect main-view-path))
      ;; todo: some kind of error message? Just assume will never happen?
      (calendar-list ctx))))

(defn main [context]
  (s/assert ::context context)
  [:body
   [:h1 "Calendar Sync"]
   ods-upload
   (current-calendar context)
   [:br]
   [:br]
   (google-login context)
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
  (response/redirect (get-oauth-redirect!)))

(comment
  (.plusSeconds (java.time.LocalDateTime/now) 3600)

 (def members (:members *1))

 members 
 (map :name members)
  )