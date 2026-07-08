(ns church-calendar-sync.unit.import-test
  (:require [church-calendar-sync.import :refer [day-strs->isolated-days
                                                 isolated-days->services]]
            [church-calendar-sync.import.calendar-grid 
             :refer [consecutive?] :as grid]
            [church-calendar-sync.integration.expected-results :refer [june]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(deftest test-consecutive
  (testing "true for sequential days within given month"
    (is (consecutive? 1 2))
    (is (consecutive? 5 6))
    (is (consecutive? 16 17)))
  (testing "true for end of month transitions"
    (is (consecutive? 28 1))
    (is (consecutive? 29 1))
    (is (consecutive? 30 1))
    (is (consecutive? 31 1)))
  (testing "false for non-consective within given month"
    (is (not (consecutive? 23 26)))
    (is (not (consecutive? 23 2)))
    (is (not (consecutive? 3 9))))
  (testing "false for 'almost end-of-month'"
    (is (not (consecutive? 31 2)))
    (is (not (consecutive? 30 2)))
    (is (not (consecutive? 32 1)))
    (is (not (consecutive? 27 1)))))

(def ^:const keep-continuous-data
  [[{:text "'1 Aug, 2026"} {:text ""} {:text ""}]
   [{:text "2"} {:text ""} {:text ""}]
   [{:text "3"} {:text ""} {:text ""}]
   [{:text "4"} {:text ""} {:text ""}]
   [{:text "22"} {:text ""} {:text ""}]
   [{:text "23"} {:text ""} {:text ""}]
   [{:text "24"} {:text ""} {:text ""}]])

(deftest test-keep-continuous
  (testing "works as expected"
    (is (=  (take 4 keep-continuous-data)
            (grid/keep-first-continuous keep-continuous-data)))))

(def ^:const expected-isolated-days
  [{:isolated-day/day 3
    :isolated-day/hours 9
    :isolated-day/minutes 0
    :service/type :service-type/liturgy
    :service/feast "Sts. Constantine and Helen"}

   {:isolated-day/day 3
    :isolated-day/hours 18
    :isolated-day/minutes 0
    :service/type :service-type/moleben
    :service/feast  "Moleben & Akathist to the Theotokos"}])

(def ^:const test-day-values
  ["3" "Sts." "Constantine" "and" "Helen" "Div." "Liturgy" "0900" "Moleben" "&" "Akathist" "to" "the" "Theotokos" "1800"])

(deftest test-day-string-parsing
  (s/check-asserts true)

  (testing "function works as expected"
    (is (= expected-isolated-days
           (day-strs->isolated-days test-day-values))))
  
  (testing "all-english parsing"
    (is (= [{:isolated-day/day 5
             :isolated-day/hours 18
             :isolated-day/minutes 0
             :service/type :service-type/weekday-evening
             :service/all-english? true}]
           (day-strs->isolated-days ["5" "All-English" "Cycle" "Evening" "Services" "1800"]))))
  
  (testing "start of month and extraneous text parsing"
    (is (= [{:isolated-day/day 1
             :isolated-day/hours 8
             :isolated-day/minutes 0,
             :isolated-day/month 6,
             :isolated-day/year 2026,
             :service/feast "Holy Spirit Day"
             :service/type :service-type/liturgy}]
           (day-strs->isolated-days ["'1 June, 2026" "Holy" "Spirit" "Day" "Div." "Liturgy" "0800" "NON-FASTING" "WEEK"])))))

(def test-isolated-merge
  (concat [{:isolated-day/month 6
            :isolated-day/day 1
            :isolated-day/year 2026} 
           
           {:isolated-day/day 2
            :isolated-day/hours 18
            :isolated-day/minutes 0
            :service/all-english? true
            :service/type :service-type/weekday-evening}]
          (assoc-in expected-isolated-days [0 :service/all-english?] true)))

(def expected-merged-services
  [{:event/date-time (june 2 18)
    :service/all-english? true
    :service/type :service-type/weekday-evening
    :service/feast "Sts. Constantine and Helen"}
   
   {:event/date-time (june 3 9)
    :service/all-english? true
    :service/type :service-type/liturgy
    :service/feast "Sts. Constantine and Helen"}
   
   {:service/feast "Moleben & Akathist to the Theotokos"
    :service/type :service-type/moleben
    :event/date-time (june 3 18)}])

(def all-expected-keys (into #{} (mapcat keys expected-merged-services)))

(deftest test-isolated-days->services
  (testing "works as expected"
    (is (= expected-merged-services 
           (->> test-isolated-merge
                isolated-days->services
                (map #(select-keys % all-expected-keys)))))))