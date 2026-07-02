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
      (cons (assoc current-day :service/date-time
                   (LocalDateTime/of (:isolated-day/year current-m-y) 
                                     (:isolated-day/month current-m-y) 
                                     (:isolated-day/day current-day)
                                     (:isolated-day/hours current-day 0)
                                     (:isolated-day/minutes current-day 0)
                                     0)) ;; seconds
            (assoc-full-date-times (update-current current-m-y current-day) other-days))))))

(defn- process-day-pair [[day1 day2]]
  (let [service-types [(:service/type day1) (:service/type day2)]]
    (if (or (= [:service-type/weekday-evening :service-type/liturgy] service-types)
            (= [:service-type/vigil :service-type/liturgy] service-types))
      [(as-> day2 * (:service/feast * "") (assoc day1 :service/feast *))
       (some->> day1 (:service/all-english?) (assoc day2 :service/all-english?))]
      [day1 day2])))

(defn isolated-days->services [days]
  (s/assert (s/coll-of ::isolated-day) days)
  (->> days
       (assoc-full-date-times)
       (remove #(not (contains? % :service/type))) ;; no type key at all means just a blank day
       (partition 2 1)
       (mapcat process-day-pair)
       (group-by (juxt :service/date-time :service/type))
       (vals)
       (map (partial apply merge))
       (s/assert ::services))) ;; todo: remove this laster form once testing is over?

(s/def :isolated-day/day (into #{} (range 1 32)))
(s/def :isolated-day/year (into #{} (range 2026 2071)))
(s/def :isolated-day/month (into #{} (range 1 13)))
(s/def :isolated-day/hours int?)
(s/def :isolated-day/minutes int?)

(s/def ::isolated-day
  (s/keys
   :req [:isolated-day/day]

   :opt [:isolated-day/month
         :isolated-day/hours
         :isolated-day/minutes
         :isolated-day/year
         :service/type
         :service/feast]))

(defn parse-int [str]
  (try
    (Integer. str)
    (catch NumberFormatException e nil)))

(defn- time-str? [next-str]
  (and (= 4 (count next-str))
       (parse-int (str/join (take 2 next-str)))
       (parse-int (str/join (drop 2 next-str)))))

(def ^:const service-type-map
  {"Div. Liturgy" :service-type/liturgy
   "Evening Services" :service-type/weekday-evening
   "Vigil" :service-type/vigil
   "Moleben" :service-type/moleben
   "" :service-type/unknown})

(def ^:const all-english "All-English Cycle")

(def non-feast-texts
  (->> (dissoc service-type-map "Moleben" "")
       keys
       (remove empty?)
       (cons all-english)
       (str/join "|")
       re-pattern))

(defn- match-service-type [text-str]
  (->> service-type-map
       (filter (fn [[service-desc _]] (str/includes? text-str service-desc)))
       (first)
       (val)))

(defn- service-details [words]
  (let [text-str (str/join " " words)]
    {:service/all-english? (or (str/includes? text-str all-english) nil) ;; so remove-vals later can clean up unknown
     :service/type (match-service-type text-str)
     :service/feast (-> text-str
                        (str/replace non-feast-texts "")
                        (str/trim))}))

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

(defn day-strs->isolated-days [day-strs]
  {:pre [(s/assert (s/coll-of string?) day-strs)]
   :post [(s/assert (s/+ ::isolated-day) %)]}
  (let [day-entities (str->day-entities (first day-strs))]
    (loop [results []
           words (rest day-strs)]
      (let [service-info (take-until time-str? words)
            time-str (last service-info)]
        (if (not (time-str? time-str))
          (if (empty? results) [day-entities] results)
          (recur (conj results (-> (drop-last service-info) 
                                   (service-details)
                                   (merge (from-time time-str) day-entities)
                                   (remove-vals #(if (seqable? %) (empty? %) (nil? %)))))
                 (drop (count service-info) words)))))))

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