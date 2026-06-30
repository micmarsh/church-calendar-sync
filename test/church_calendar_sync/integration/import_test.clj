(ns church-calendar-sync.integration.import-test 
  (:require
    [church-calendar-sync.import.jopendocument :refer [sheet-from-file-path]]
    [clojure.spec.alpha :as s]))


(-> (java.io.File. "") .getAbsolutePath)


(s/check-asserts true)
(sheet-from-file-path (str (-> (java.io.File. "") .getAbsolutePath) "/test/church_calendar_sync/integration/TestSheetJune2026.ods"))