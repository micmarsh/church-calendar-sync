(defproject church-calendar-sync "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://example.com/FIXME"
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
                 [com.widdindustries/time-literals "0.1.10"]]
  :main church-calendar-sync.core
  :repl-options {:init-ns church-calendar-sync.core})
