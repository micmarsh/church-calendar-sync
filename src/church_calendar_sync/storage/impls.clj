(ns church-calendar-sync.storage.impls 
  (:require
    [church-calendar-sync.google.oauth.storage :as storage]
    [church-calendar-sync.spec :as spec]
    [church-calendar-sync.storage.config :refer [ConfigStorage get-config]]
    [church-calendar-sync.utils :refer [parse-edn]]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]))

(defonce storage-atom (atom nil))
(extend-type clojure.lang.Atom
  storage/TokenStorage 
  (-get [a] (:token-storage (deref a)))
  (-put [a item] (swap! a assoc :token-storage item))
  ConfigStorage
  (get-config [a k] (get-in @a [:config k]))
  (put-config! [a k v] (swap! a assoc-in [:config k] v)))

(def storage-file (delay (io/file (str (System/getProperty "user.home") "/.config/calendar_sync.edn"))))
(defn- file-contents [^java.io.File f]
  (-> f (.getParentFile) (.mkdirs))
  (.createNewFile f) 
  (let [config-str (slurp (.getPath f))] 
    (if (empty? config-str) 
      { } 
      (parse-edn config-str))))

(extend-type java.io.File
  storage/TokenStorage
  (-get [f] (:token-storage (file-contents f)))
  (-put [f item] (as-> f *
                   (file-contents *)
                   (assoc * :token-storage item) 
                   (spit (.getPath f) (str *))))
  ConfigStorage
  (get-config [f k] (get-in (file-contents f) [:config k]))
  (put-config! [f k v] (as-> f *
                         (file-contents *)
                         (assoc-in * [:config k] v)
                         (spit (.getPath f) (str *)))))


(defn get-saved-settings [{:keys [token-storage config-storage] :as ctx}]
  (s/assert ::spec/storage ctx)
  {:auth (storage/get-token token-storage)
   :calendar (get-config config-storage :church-calendar-sync.app/current-calendar)})