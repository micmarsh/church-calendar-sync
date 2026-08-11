(defproject church-calendar-sync "0.1.0"
  :description "Tool for synchronizing a STV service schedule spreadsheet to gCal, PDF, and (eventually) Google sheets service sign-up"
  :url "https://github.com/micmarsh/church-calendar-sync"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [org.jopendocument/jOpenDocument "1.3"]
                 [org.clojure/data.json "2.5.2"]
                 [org.clojure/core.match "1.1.1"]
                 [http-kit/http-kit "2.8.1"]
                 [ring/ring-core "1.15.5"]
                 [camel-snake-kebab "0.4.3"]
                 [hiccup "2.0.0"]
                 [com.widdindustries/time-literals "0.1.10"]
                 [org.clojure/core.async "1.9.865"]]
  :main church-calendar-sync.core
  :repl-options {:init-ns church-calendar-sync.core}

  :profiles { :uberjar {:aot :all} }
  :plugins [[lein-jdeb "0.2.2"]]

  ;; https://github.com/r4um/lein-jdeb/blob/master/src/leiningen/jdeb.clj#L53
  :deb-depends "libreoffice, default-jre | java7-runtime | java6-runtime" 
  :deb-maintainer "Michael Marsh <mike@marsh.pw>"
  )
