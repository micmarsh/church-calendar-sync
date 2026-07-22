(ns church-calendar-sync.storage.config)

(defprotocol ConfigStorage 
  (get-config [this key'])
  (put-config! [this key' value]))