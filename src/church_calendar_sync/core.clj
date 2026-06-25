(ns church-calendar-sync.core)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))


(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods")

  (require '[church-calendar-sync.jopendocument :refer [sheet->clj]])

  (ns church-calendar-sync.jopendocument)

  sheet->clj
  (use 'clojure.reflect)
  (reflect sheet)

  )
  