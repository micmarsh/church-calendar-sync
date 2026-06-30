(ns church-calendar-sync.spec
  (:require
   [clojure.spec.alpha :as s]))

(defn gte-int? [min] (s/and int? (fn [num] (>= num min))))
(s/def :general/gte-0-int (gte-int? 0))

(s/def :grid/row :general/gte-0-int)

(s/def :grid/column :general/gte-0-int)

(s/def :config/day-width (gte-int? 1))
(s/def :config/day-height (gte-int? 1))
(s/def :config/start-row :grid/row)
(s/def :config/start-column :grid/column)
(s/def :config/end-row :grid/row)
(s/def :config/end-column :grid/column)

(s/def ::config
  (s/keys :req-un
          [:config/day-height
           :config/day-width
           :config/start-row
           :config/end-row
           :config/start-column
           :config/end-column]))

(s/def :cell/row :grid/row)
(s/def :cell/column :grid/column)
(s/def :cell/text string?)

(s/def ::cell (s/keys :req-un [:cell/column :cell/row :cell/text]))

(s/def :service/date-time inst?)
(s/def :service/name (s/and not-empty string?))
(s/def :service/feast string?) ;; usually "saint", but more general
(s/def :service/all-english? boolean?)
(s/def :service/type #{:liturgy :weekday-evening :vigil :moleben})

(s/def ::service 
  (s/keys :req-un [:service/date-time
                   :service/name
                   :service/all-english?
                   :service/feast
                   :service/type]))

