(ns church-calendar-sync.import-test
  (:require [church-calendar-sync.import.calendar-grid 
             :refer [consecutive?] :as grid]
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
