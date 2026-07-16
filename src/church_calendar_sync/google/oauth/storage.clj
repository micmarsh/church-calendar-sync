(ns church-calendar-sync.google.oauth.storage
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.utils :refer [gt-date]]
   [clojure.spec.alpha :as s]))

(defprotocol TokenStorage
  (-get [this])
  (-put [this token]))

(defn put! [this token]
  (s/assert ::oauth/expiring-token-result token)
  (-put this token))

(defn get [this]
  {:post [(s/assert (s/nilable ::oauth/expiring-token-result) %)]}
  (when-let [token (-get this)]
    (when (gt-date (:expires token) (java.time.LocalDateTime/now))
      token)))