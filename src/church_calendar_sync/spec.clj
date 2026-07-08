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

(s/def :event/date-time (partial instance? java.time.LocalDateTime))
(s/def :service/feast string?)
(s/def :event/description string?)
(s/def :service/all-english? boolean?)

(def ^:const event-types
  #{:event-type/service
    :event-type/confession})

(def ^:const service-types
  #{:service-type/liturgy
    :service-type/weekday-evening
    :service-type/vigil
    :service-type/moleben
    ;; these last ones will require more attention later on user-level
    :service-type/hours
    :service-type/unknown})

(s/def :event/type event-types)
(s/def :service/type service-types)

(defmulti event-spec :event/type)

(defmethod event-spec :event-type/service 
  [event]
  (case (:service/type event)
    :service-type/moleben (s/keys :req [:event/date-time
                                        :event/description
                                        :service/type])
    :service-type/vigil (s/keys :req [:event/date-time 
                                      :service/type]
                                :opt [:service/all-english?
                                      ;; only relevant for last day of calendar
                                      ;; no good option here
                                      :service/feast])
    (s/keys :req [:event/date-time
                  :service/feast
                  :service/type]
            :opt [:service/all-english?])))

;; backwards-compatability
(defmethod event-spec nil [e]
  (-> e
      (assoc :event/type :event-type/service)
      (event-spec)))

(defmethod event-spec :event-type/confession [_]
  (s/keys :req [:event/date-time]
          :opt [:event/description
                :service/feast]))

(s/def ::service 
  (s/multi-spec event-spec :event/type))

