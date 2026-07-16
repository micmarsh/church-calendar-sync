(ns church-calendar-sync.google.oauth.storage
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.utils :refer [gt-date]]
   [clojure.spec.alpha :as s]))

(defprotocol TokenStorage
  (-get [this])
  (-put [this token]))

(defn put-token! [this token]
  (s/assert ::oauth/expiring-token-result token)
  (-put this token))

(defn get-token [this]
  {:post [(s/assert (s/nilable ::oauth/expiring-token-result) %)]}
  (when-let [token (-get this)]
    (when-let [expires (:expires token)] 
      (when (gt-date expires (java.time.LocalDateTime/now))
        token))))