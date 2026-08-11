(ns church-calendar-sync.export
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

(def ^:const out-dir "/tmp/")

(defn- invoke-libre-office [ods-file-path]
  (let [parts (str/split ods-file-path #"/")]
    (shell/sh "libreoffice" "--headless" "--convert-to" "pdf" ods-file-path "--outdir" out-dir)
    (str out-dir (first (str/split (last parts) #"\.")) ".pdf")))

;; TODO somehow needs messaging when libreoffice isn't installed, maybe is fine though?
(defn export-to-pdf [ods-file-path]
  (->> ods-file-path
       (invoke-libre-office)
       (io/file)))
