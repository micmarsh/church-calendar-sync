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

;; todo test google api with expired tokens, make sure it actually matters!
(defn get-token [this]
  (->>
   (when-let [token (-get this)]
     (when-let [expires (:expires token)]
       (when (gt-date expires (java.time.LocalDateTime/now))
         token)))
   ;; can't do check in :post b/c nil will fail function assertion!
   (s/assert (s/nilable ::oauth/expiring-token-result))))