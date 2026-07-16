(ns church-calendar-sync.utils
  (:import [java.time ZoneId])
  (:require
   [clojure.data.json :as json]
   [camel-snake-kebab.core :as csk]))

(defn take-until
  "https://groups.google.com/g/clojure-dev/c/NaAuBz6SpkY?pli=1
   
   Returns a lazy sequence of successive items from coll until
   (pred item) returns true, including that item. pred must be
   free of side-effects."
  [pred coll]
  (lazy-seq
   (when-let [s (seq coll)]
     (if (pred (first s))
       (cons (first s) nil)
       (cons (first s) (take-until pred (rest s)))))))

(defn remove-vals [map f]
  (into {} (remove (comp f val) map)))

(defn- millis [^java.time.LocalDateTime date]
  (-> date
      (.atZone (ZoneId/of "America/Chicago"))
      (.toInstant)
      (.toEpochMilli)))

(defn gt-date [^java.time.LocalDateTime date1 ^java.time.LocalDateTime date2]
  (> (millis date1) (millis date2)))

(defn sort-by-date [date-key coll]
  (sort-by (comp millis date-key) coll))

(defn parse-json [string] (json/read-str string :key-fn csk/->kebab-case-keyword))

(defn- equals-cond-clauses [value forms]
  (mapcat (fn [[val body]]
            [(list 'clojure.core/= val value) body])
          (partition 2 forms)))

(defmacro match=
  "Much less fancy than core.match: compiles to plain `cond` statements so we can
   use ref names in condtionals"
  [value & forms]
  (if (even? (count forms))
    `(cond
       ~@(equals-cond-clauses value forms))
    `(cond
       ~@(equals-cond-clauses value (drop-last forms))
       :else ~(last forms))))

;; should be ready to go if "needed", just add core.async!
;; maybe make this do promise -> chan / chan -> chan, then make sure blocking deref is implemented on chans for "outside world"?
;; or just tell "outside world" to deal with it?
#_(defn fmap [f p]
   (let [result-p (promise)]
     (a/go-loop []
       (if (realized? p)
         (deliver result-p (f @p))
         (do
           (<! (a/timeout 100))
           (recur))))
     result-p))