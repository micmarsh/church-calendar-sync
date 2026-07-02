(ns church-calendar-sync.import
  (:require
   [church-calendar-sync.import.calendar-grid :as grid]
   [church-calendar-sync.import.jopendocument :as ods]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [remove-vals take-until]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::services (s/coll-of ::spec/service))

;; todo don't want day strings, instead want ["Date (day or date)" "Word1" "Word2" "0900", ...]?
(defn- day-group->day-strs [day-width [date-cell & rest]]
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

(defn- in-progress? [acc]
  (or (not-empty (:current-text acc))
      (not-empty (:current-map acc))))

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

(defn- add-current
  [{:keys [total-results current-map] :as acc}]
  {:pre [(s/assert ::build-isolated-service acc)]
   :post [(s/assert :build-iso/total-results %)]}
  (conj total-results (merge current-map (service-details acc))))

(defn- from-time [next-str]
  {:isolated-day/hours (parse-int (str/join (take 2 next-str)))
   :isolated-day/minutes (parse-int (str/join (drop 2 next-str)))})

(defn- day-str? [next-str]
  (grid/day-of-month? (str/replace next-str "'" "")))

(defn- assoc-day [{:keys [current-map potential-full-date] :as acc} next-str]
  ;; want to assoc day and also add to others in "total", given it's per day
  (if (empty? potential-full-date)
    (let [day (parse-int next-str)])
    nil))

(s/def :build-iso/current-map map?)
(s/def :build-iso/potential-full-date (s/and list? (s/* string?)))
(s/def :build-iso/current-text (s/and list? (s/* string?)))
(s/def :build-iso/total-results (s/and vector? (s/* ::isolated-day)))
(s/def :build-iso/day-results (s/and vector? (s/* :build-iso/current-map)))

(s/def ::build-isolated-service
  (s/keys :req-un
          [:build-iso/current-map
           :build-iso/potential-full-date
           :build-iso/current-text
           :build-iso/total-results
           :build-iso/day-results]))

(def ^:const empty-build-steps
  {:total-results [] :current-map {} :day-results [] :current-text '() :potential-full-date '()})

;; todo!? Make this the foundation of everything (maybe not, wait)
(defn- build-iso-service-step [{:keys [current-map] :as acc} next-str]
  {:pre [(s/assert ::build-isolated-service acc)]
   :post [(s/assert ::build-isolated-service %)]}
  (throw (Exception. (str "TODO: implement " 'build-iso-service-step " or rip it out in favor of more sensical method (see below)")))
  #_(cond
      ;; the end of a day, add everything to all maps for that day and forward them on to "results"
      (day-str? next-str) (as-> acc *
                            (update * :total-results into (full-day-results acc next-str))
                            (merge * (dissoc empty-build-steps :total-results)))

      ;; encountering a time adds everything so far to "day-results", starts working on a new "current"
      (and (time-str? next-str) (in-progress? acc)) ()  #_(assoc acc
                                                                 :total-results (add-current acc)
                                                                 :current-text '()
                                                                 :current-map (from-time next-str))

      ;; encountering a time at the very start of a day is expected
      (time-str? next-str) (assoc acc :current-map (from-time next-str))

      =    (grid/contains-year? next-str) (assoc acc :potential-full-date (list next-str))

      (grid/contains-month? next-str) (update acc :potential-full-date conj next-str)

      :else (update acc :current-text conj next-str)))

(defn- month-str->int [month]
  (some->> (map-indexed vector grid/months)
           (filter #(str/includes? month (second %)))
           (ffirst)
           (inc)))

(defn- str->day-entities [day-str]
  (let [[day month year] (remove empty? (str/split day-str #" |'|,"))
        result  {:isolated-day/day (parse-int day)
                 :isolated-day/year (parse-int year)
                 :isolated-day/month (some-> month month-str->int)}]
    result))

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


(defn- day-groups->services [{:keys [day-width]} day-groups]
  (->> day-groups
       (map (partial day-group->day-strs day-width))
       (mapcat day-strs->isolated-days)
       (isolated-days->services)))

(defn ods-sheet->services [config sheet]
  (s/assert ::spec/config config)
  (s/assert ::ods/sheet sheet)
  (->> (ods/sheet->clj config sheet)
       (grid/group-days config)
       (day-groups->services config)
       (s/assert ::services)))

(comment
  (def ^:const ods-file-name "/home/michael/Documents/FrGregoryCalendarjune2026.ods")

  (def ^:const config
    {:start-row 10
     :start-column 0
     :day-width 3
     :day-height 8
     :end-column 25
     :end-row 100})

  (s/check-asserts true)
  (def sheet (ods/sheet-from-file-path ods-file-name))

  (->> sheet
       (ods/sheet->clj config)
       (map #(dissoc % :java-cell))

       (group-days config)

       #_(map first)))