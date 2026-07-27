(ns church-calendar-sync.app
  (:require [church-calendar-sync.app.processing-upload :as processing-upload]
            [church-calendar-sync.storage.config :as config]
            [church-calendar-sync.google.gcal :as gcal]
            [church-calendar-sync.google.oauth :as oauth]
            [church-calendar-sync.google.oauth.storage :as storage]
            [church-calendar-sync.spec :as spec]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ring.util.response :as response]
            [clojure.edn :as edn]))

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

(def ^:const uploaded-file-name processing-upload/uploaded-file-name)

(def ^:const upload-view-path "/ods-upload")

(defn ods-upload [ctx]
  [:form {:action upload-view-path :method "post" :enctype "multipart/form-data"}
   "Select file to upload: "
   [:input {:type "file" :name uploaded-file-name}]
   [:p]
   [:input (cond-> {:type "submit" :value "Upload"}
             (s/valid? ::spec/syncable-req-ctx ctx) (assoc :disabled true))]])

(defn- google-login [ctx]
  (s/assert ::spec/base-req-ctx ctx)
  (if (s/valid? ::spec/oauthed-req-ctx ctx)
    [:div "Logged into google successfully"]
    [:div [:a {:href (oauth/get-raw-oath-url ctx)} "Log in to Google"]]))

(def ^:const calendar-list-path  "/calendar-list")

(defn- current-calendar [{:keys [config-storage]}]
  (if-let [{:keys [summary id]} (config/get-config config-storage ::current-calendar)]
    [:div 
     [:div "Will sync to calendar \"" [:a {:href (str "https://calendar.google.com/calendar/u/0/r?cid=" id)} summary] \"]
     [:a {:href calendar-list-path} "select a different calendar"]] ;; todo store readable name and id, display readable name? we'll see
    [:div [:a {:href calendar-list-path} "Select a calendar to sync to"]]))

(def ^:const select-calendar-path "/select-calendar")

(def ^:const select-calendar-param "calendar-selection")

(defn calendar-list [{::oauth/keys [expiring-token-result] :as ctx}]
  (s/assert ::spec/oauthed-req-ctx ctx)
  (let [calendars (->> (gcal/get-calendars expiring-token-result) :body :items (filter (comp #{"owner"} :access-role)))]
    [:body
     [:h2 "Select a Calendar to Sync to"]
     [:form {:action select-calendar-path :method "post"}
      (for [{:keys [id summary] :as cal} calendars
            node [[:input {:type "radio" :name select-calendar-param :value (str (select-keys cal [:id :summary :time-zone])) :id id}]
                  [:label {:for id} summary]
                  [:br]]]
        node)
      [:input {:type "submit" :value "Submit"}]]]))


#_(do
    (set-oauth-redirect! calendar-list-path)
    (response/redirect (oauth/get-raw-oath-url ctx)))

#_(swap! church-calendar-sync.core/storage-atom assoc :config nil)

(defn select-calendar [{:keys [config-storage] :as ctx} {:keys [params] :as req}]
  (s/assert ::spec/base-req-ctx ctx) 
  (config/put-config! config-storage ::current-calendar (-> params (get select-calendar-param) (edn/read-string)))
  (response/redirect main-view-path))

(defn main [context]
  (s/assert ::spec/base-req-ctx context)
  [:body
   [:h1 "Calendar Sync"]
   (ods-upload context)
   (current-calendar context)
   [:br]
   [:br]
   (google-login context)
   #_htmx-load])

(def processing-upload processing-upload/run-initial)

(def ^:const sync-to-calendar-path processing-upload/sync-to-calendar-path)

(def sync-to-calendar processing-upload/sync-to-calendar)

(defn- assoc-expires-time [{:keys [expires-in] :as token-result}]
  (assoc token-result :expires (.plusSeconds (java.time.LocalDateTime/now) expires-in)))

(defn oauth-get-token [{:keys [token-storage] :as context} req]
  (s/assert ::spec/base-req-ctx context)
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