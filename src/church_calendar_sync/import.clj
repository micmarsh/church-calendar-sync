(ns church-calendar-sync.import
  (:require
   [church-calendar-sync.import.calendar-grid :as grid]
   [church-calendar-sync.import.jopendocument :as ods]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [remove-vals take-until]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str])
  (:import [java.time LocalDateTime]))

(s/def ::services (s/coll-of ::spec/service))

(defn- day-group->day-strs [[date-cell & rest]]
  (lazy-seq
   (cons (:text date-cell)
         (for [{:keys [text]} rest
               word (str/split text #" ")
               :when (not-empty word)]
           word))))

(s/def ::full-date-day
  (s/keys :req [:isolated-day/year :isolated-day/month :isolated-day/day]))

(defn- update-current [current-m-y current-day]
  {:post [(s/assert ::full-date-day %)]}
  (if (and (contains? current-day :isolated-day/year) (contains? current-day :isolated-day/month)
           (or (not= (:isolated-day/month current-day) (:isolated-day/month current-m-y))
               (not= (:isolated-day/year current-day) (:isolated-day/year current-m-y))))
    current-day
    current-m-y))

(defn- assoc-full-date-times
  ([[start-day :as days]]
   (s/assert ::full-date-day start-day)
   (assoc-full-date-times start-day days))
  ([current-m-y [current-day & other-days]]
   (lazy-seq
    (if (nil? current-day)
      '()
      (cons (assoc current-day :event/date-time
                   (LocalDateTime/of (:isolated-day/year current-m-y)
                                     (:isolated-day/month current-m-y)
                                     (:isolated-day/day current-day)
                                     (:isolated-day/hours current-day 0)
                                     (:isolated-day/minutes current-day 0)
                                     0)) ;; seconds
            (assoc-full-date-times (update-current current-m-y current-day) other-days))))))

(defn- service? [day]
  (= (namespace :service-type/liturgy) 
     (namespace (:isolated-day/type day))))

(defn- other-event? [day]
  (= (namespace :event-type/confession) 
     (namespace (:isolated-day/type day))))

(defn- process-day-group [day-group]
  (let [feast-name (->> day-group (filter (comp #{:service-type/liturgy} :isolated-day/type)) (first) (:event/description))
        all-english? (->> day-group 
                          (filter (comp #{:service-type/weekday-evening :service-type/vigil} :isolated-day/type))
                          (first)
                          (:service/all-english?))]
    (for [day day-group
          :let [type (:isolated-day/type day)]]
      (cond-> day 
        (other-event? day) (assoc :event/type type)
        (other-event? day) (dissoc :event/description)
        (service? day) (assoc :service/type type)
        (service? day) (dissoc :event/description)
        (and (not= :service-type/moleben type) (not (nil? feast-name))) (assoc :service/feast feast-name)
        (not (nil? all-english?)) (assoc :service/all-english? all-english?)))))

(str :foo/bar)
(namespace :foo/bar)

(defn- start-group? [day]
  (#{:service-type/weekday-evening :service-type/vigil} (:isolated-day/type day)))

(defn- end-group? [day]
  (= :service-type/liturgy (:isolated-day/type day)))

(defn group-by-service-cycle [days']
  {:post [(s/assert (s/coll-of (s/coll-of ::isolated-day)) %)]}
  (loop [final-results []
         current-group []
         [current-day & next-days] days']
    (cond
      (nil? current-day) (conj final-results current-group)
      (start-group? current-day) (recur (conj final-results current-group)
                                        [current-day]
                                        next-days)
      (end-group? current-day) (recur (conj final-results (conj current-group current-day))
                                      []
                                      next-days)
      :else (recur final-results
                   (conj current-group current-day)
                   next-days))))

(defn isolated-days->services [days]
  (s/assert (s/coll-of ::isolated-day) days)
  (->> days
       (assoc-full-date-times)
       (remove #(not (contains? % :isolated-day/type))) ;; no type key at all means just a blank day
       (group-by-service-cycle)
       (remove empty?)
       (mapcat process-day-group)
       (group-by (juxt :event/date-time :isolated-day/type))
       (vals)
       (map (partial apply merge))
       (map #(do (s/assert ::spec/service %) %))
       (s/assert ::services))) ;; todo: remove this last form once testing is over? Perhaps remove from "main function" instead?

(def ^:const service-type-map
  {"Div. Liturgy" :service-type/liturgy
   "Evening Services" :service-type/weekday-evening
   "Vigil" :service-type/vigil
   "Moleben" :service-type/moleben
   "Hours" :service-type/hours
   "Confession" :event-type/confession
   "" :service-type/unknown})

(s/def :isolated-day/day (into #{} (range 1 32)))
(s/def :isolated-day/year (into #{} (range 2026 2071)))
(s/def :isolated-day/month (into #{} (range 1 13)))
(s/def :isolated-day/hours (into #{} (range 0 24)))
(s/def :isolated-day/minutes (into #{} (range 0 60)))
(s/def :isolated-day/type 
  (into #{} (vals service-type-map)))

(s/def ::isolated-day
  (s/keys
   :req [:isolated-day/day]

   :opt [:isolated-day/month
         :isolated-day/hours
         :isolated-day/minutes
         :isolated-day/year
         :isolated-day/type 
         :event/description]))

(defn parse-int [str]
  (try
    (Integer. str)
    (catch NumberFormatException e nil)))

(defn- time-str? [next-str]
  (and (= 4 (count next-str))
       (parse-int (str/join (take 2 next-str)))
       (parse-int (str/join (drop 2 next-str)))))

(def ^:const all-english "All-English Cycle")

(def non-feast-texts
  (->> (dissoc service-type-map "Moleben" "")
       (keys)
       (cons all-english)
       (str/join "|")
       (re-pattern)))

(def non-feast-words
  (as-> non-feast-texts *
    (str *)
    (str/split * #"\|| ")
    (str/join "|" *)
    (re-pattern *)))

(defn- match-service-type [text-str]
  (->> service-type-map
       (filter (fn [[service-desc _]] (str/includes? text-str service-desc)))
       (first)
       (val)))

(defn- service-details [words]
  (let [text-str (str/join " " words)]
    {:service/all-english? (or (str/includes? text-str all-english) nil) ;; so remove-vals later can clean up unknown
     :isolated-day/type (match-service-type text-str)}))

(defn- from-time [next-str]
  {:isolated-day/hours (parse-int (str/join (take 2 next-str)))
   :isolated-day/minutes (parse-int (str/join (drop 2 next-str)))})

(defn- month-str->int [month]
  (some->> (map-indexed vector grid/months)
           (filter #(str/includes? month (second %)))
           (ffirst)
           (inc)))

(defn- str->day-entities [day-str]
  (let [[day month year] (remove empty? (str/split day-str #" |'|,"))]
    (-> {:isolated-day/day (parse-int day)
         :isolated-day/year (parse-int year)
         :isolated-day/month (some-> month month-str->int)}
        (remove-vals nil?))))

(defn- take-desc-words [day-strs]
  (take-while #(not (or (time-str? %) (re-matches non-feast-words %))) day-strs))

(defn- day-services [results words]
  (let [service-info (take-until time-str? words)
        time-str (last service-info)]
    (if (not (time-str? time-str))
      (if (empty? results) [{}] results)
      (recur (conj results (-> (drop-last service-info)
                               (service-details)
                               (merge (from-time time-str))))
             (drop (count service-info) words)))))

(defn day-strs->isolated-days [[day & rest :as day-strs]]
  {:pre [(s/assert (s/coll-of string?) day-strs)]
   :post [(s/assert (s/+ ::isolated-day) %)]}
  (let [day-entities (str->day-entities day)
        description (take-desc-words rest)]
    (for [day-service (day-services [] (drop (count description) rest))]
      (-> day-service
          (merge day-entities)
          (remove-vals #(if (seqable? %) (empty? %) (nil? %)))
          (assoc :event/description
                 (-> (str/join " " description)
                     (str/replace non-feast-texts "")
                     (str/trim)))))))

(defn- day-groups->services [day-groups]
  (->> day-groups
       (map day-group->day-strs)
       ;; these last two steps are integration tested together!
       (mapcat day-strs->isolated-days)
       (isolated-days->services)))

(defn ods-sheet->services [config sheet]
  (s/assert ::spec/config config)
  (s/assert ::ods/sheet sheet)
  (->> (ods/sheet->clj config sheet)
       (grid/group-days config)
       (day-groups->services)
       (s/assert ::services)))