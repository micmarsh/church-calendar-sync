(ns church-calendar-sync.integration.import-test
  (:require
   [church-calendar-sync.import :refer [ods-sheet->services]]
   [church-calendar-sync.import.jopendocument :refer [sheet-from-file-path]]
   [church-calendar-sync.integration.expected-results :as results]
   [clojure.spec.alpha :as s]
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

(deftest test-fully-parse-spreadheet
  (testing "test spreadsheet parses to expected results"
    (s/check-asserts true)
    (is (= results/expected-services
           (->> @test-file-path
                (sheet-from-file-path)
                (ods-sheet->services test-config)
                (map #(select-keys % all-expected-keys)))))))

