(ns church-calendar-sync.import
  (:require
   [church-calendar-sync.import.calendar-grid :as grid]
   [church-calendar-sync.import.jopendocument :as ods]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [remove-vals take-until]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::services (s/coll-of ::spec/service))

(defn- day-group->day-strs [[date-cell & rest]]
  (lazy-seq
   (cons (:text date-cell)
         (for [{:keys [text]} rest
               word (str/split text #" ")
               :when (not-empty word)]
           word))))

(def ^:private not-nil
  #(if (nil? %1) %2 %1))

(defn isolated-days->services [days]
  (s/assert (s/coll-of ::isolated-day) days)
  (->> days
       ;; todo: need to convert "isolated-service" entities to real date-times right here!
       ;;   some kind of sequential processing
       (group-by (juxt :service/date-time :service/type))
       (vals)
       (map (fn [dup-services]
              (apply merge-with not-nil dup-services)))
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
         :service/type]))

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
    {:service/all-english? (str/includes? text-str all-english)
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
   :post [(s/assert (s/or :single-result (s/tuple ::isolated-day)
                          :two-results (s/tuple ::isolated-day ::isolated-day)) %)]}
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
       (mapcat day-strs->isolated-days)
       (isolated-days->services)))

(defn ods-sheet->services [config sheet]
  (s/assert ::spec/config config)
  (s/assert ::ods/sheet sheet)
  (->> (ods/sheet->clj config sheet)
       (grid/group-days config)
       (day-groups->services)
       (s/assert ::services)))