(ns church-calendar-sync.config-storage)

(defprotocol ConfigStorage 
  (get-config [this key'])
  (put-config! [this key' value]))