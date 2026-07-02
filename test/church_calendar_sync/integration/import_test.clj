(ns church-calendar-sync.integration.import-test
  (:require
   [church-calendar-sync.import :refer [day-strs->isolated-days
                                        isolated-days->services
                                        ods-sheet->services]]
   [church-calendar-sync.import.jopendocument :refer [sheet-from-file-path]]
   [church-calendar-sync.integration.expected-results :as results]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :refer :all]))

(def test-file-path
  (-> (java.io.File. "")
      (.getAbsolutePath)
      (str "/test/church_calendar_sync/integration/TestSheetJune2026.ods")
      (delay)))

;; is also "standard config" likely to be used in production
(def ^:const test-config
  {:start-row 10
   :start-column 0
   :day-width 3
   :day-height 8
   :end-column 25
   :end-row 100})

(def all-expected-keys (into #{} (mapcat keys results/expected-services)))

(defn- clean-for-test-compare [results]
  (->> results
       (map #(select-keys % all-expected-keys))
       (remove (comp #{:service-type/unknown} :service/type))))

(deftest test-fully-parse-spreadheet
  (testing "test spreadsheet parses to expected results"
    (s/check-asserts true)
    (is (= results/expected-services
           (->> @test-file-path
                (sheet-from-file-path)
                (ods-sheet->services test-config)
                (clean-for-test-compare))))))


(def basic-sat-sun-strings
  [(cons "5 June, 2026" (str/split "All-English Cycle Evening Services 1800" #" "))
   (str/split "6 Venerable Simeon the Stylite Div. Liturgy 0800 Confession 1700 Vigil 1800" #" ")
   (str/split "7 Sunday of All Saints Confession 0900 Hours 0930 Div. Liturgy 1000" #" ")])

(deftest test-saturday-sunday-processing
  (s/check-asserts true)
  (testing "basic handling of 'extra' unknown services"
    (is  (= results/expected-basic-sat-sun
            (->> basic-sat-sun-strings
                 (mapcat day-strs->isolated-days)
                 (isolated-days->services) 
                 (clean-for-test-compare))))))