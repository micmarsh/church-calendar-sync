(ns church-calendar-sync.utils.async 
  (:require
   [clojure.core.async :as a]))

(defn pending->chan [p]
  (a/go-loop [wait-ms 200]
    (if (realized? p)
      @p
      (do 
        (a/<! (a/timeout wait-ms))
        (recur (* 2 wait-ms))))))

(defprotocol ToChannel (->chan [obj]))
(extend-protocol ToChannel
  clojure.lang.IPending
  (->chan [obj] (pending->chan obj))
  clojure.core.async.impl.channels.ManyToManyChannel
  (->chan [obj] obj))

(defn with-retry 
  "Will returns a version of 'func' that retries according to the given schedule.
   Converts 'func' into a channel-returning function if it isn't already"
  [func schedule]
  (fn [& args]
    (a/go-loop [{:keys [max-attempts increment error? wait-ms]
                 :or {max-attempts 2 increment identity wait-ms 1000 error? nil?} :as sched} schedule
                current-attempt 1
                value (a/<! (->chan (apply func args)))]
      (if (and (error? value) (< current-attempt max-attempts))
        (do 
          (a/<! (a/timeout wait-ms))
          (recur (update sched :wait-ms increment)
                 (inc current-attempt)
                 (a/<! (->chan (apply func args)))))
        value))))