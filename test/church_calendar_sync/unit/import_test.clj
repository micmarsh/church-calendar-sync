(ns church-calendar-sync.unit.import-test
  (:require [church-calendar-sync.import :refer [day-string->isolated-services
                                                 merge-isolated-services]]
            [church-calendar-sync.import.calendar-grid 
             :refer [consecutive?] :as grid]
            [church-calendar-sync.integration.expected-results :refer [june]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]))

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

(def ^:const expected-isolated-services
  [{:isolated-service/day 3
    :isolated-service/hours 9
    :isolated-service/minutes 0
    :service/type :service-type/liturgy
    :service/feast "Sts. Constantine and Helen"}
   
   {:isolated-service/day 3
    :isolated-service/hours 18
    :isolated-service/minutes 0
    :service/type :service-type/moleben
    :service/feast  "Moleben & Akathist to the Theotokos"}])

(def ^:const test-day-string
  " 3     Sts. Constantine and Helen 0900 Moleben & Akathist to the Theotokos 1800 ")

(deftest test-day-string-parsing
  (s/check-asserts true)

  (testing "function works as expected"
    (is (= expected-isolated-services
           (day-string->isolated-services test-day-string))))
  
  (testing "all-english parsing"
    (is (= [{:isolated-service/day 5
             :isolated-service/hours 18
             :isolated-service/minutes 0
             :service/type :service-type/weekday-evening
             :service/all-english? true}]
           (day-string->isolated-services "5     All-English Cycle Evening Services 1800")))))

(def test-isolated-merge
  (concat [{:isolated-service/month 6
            :isolated-service/day 1} 
           
           {:isolated-service/day 2
            :isolated-service/hours 18
            :isolated-service/minutes 0
            :service/all-english? true
            :service/type :service-type/weekday-evening}]
          expected-isolated-services))

(def expected-merged-services
  [{:service/date-time (june 2 18)
    :service/all-english? true
    :service/type :service-type/weekday-evening
    :service/feast "Sts. Constantine and Helen"}
   
   {:service/date-time (june 3 9)
    :service/all-english? true
    :service/type :service-type/liturgy
    :service/feast "Sts. Constantine and Helen"}
   
   {:service/feast "Moleben & Akathist to the Theotokos"
    :service/type :service-type/moleben
    :service/all-english? false
    :service/date-time (june 3 18)}])

(deftest test-merge-isolated-services
  (testing "works as expected"
    (is (= expected-merged-services 
           (merge-isolated-services test-isolated-merge)))))
