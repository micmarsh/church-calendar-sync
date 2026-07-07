(ns church-calendar-sync.utils)

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
