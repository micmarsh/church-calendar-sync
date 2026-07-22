(ns church-calendar-sync.storage.impls 
  (:require
    [church-calendar-sync.google.oauth.storage :as storage]
    [church-calendar-sync.storage.config :refer [ConfigStorage]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

;; todo move these somewhere else?
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
  (edn/read-string (slurp (.getPath f))))

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