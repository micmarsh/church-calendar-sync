(ns church-calendar-sync.integration.expected-results 
  (:import [java.time LocalDateTime]))

(defn date [month day hour minute]
  (LocalDateTime/of 2026 month day hour minute 0))

(defn june
  ([day hour] (june day hour 0))
  ([day hour minute] (date 6 day hour minute)))

(defn july
  ([day hour] (july day hour 0))
  ([day hour minute] (date 7 day hour minute)))

(def expected-services
  [{:service/feast "Holy Spirit Day"
    :service/type :service-type/liturgy
    :service/date-time (june 1 8)}

   {:service/feast "Sts. Constantine and Helen"
    :service/type :service-type/weekday-evening
    :service/date-time (june 2 18)}

   {:service/feast "Sts. Constantine and Helen"
    :service/type :service-type/liturgy
    :service/date-time (june 3 9)}

   {:service/feast "Moleben & Akathist to the Theotokos"
    :service/type :service-type/moleben
    :service/date-time (june 3 18)}

   {:service/feast "Venerable Simeon the Stylite"
    :service/type :service-type/weekday-evening
    :service/all-english? true
    :service/date-time (june 5 18)}

   {:service/feast "Venerable Simeon the Stylite"
    :service/type :service-type/liturgy
    :service/all-english? true
    :service/date-time (june 6 8)}

   {:service/feast "Sunday of All Saints"
    :service/type :service-type/vigil
    :service/date-time (june 6 18)}

   {:service/feast "Sunday of All Saints"
    :service/type :service-type/liturgy
    :service/date-time (june 7 10)}

   {:service/feast "Moleben & Akathist to the Theotokos"
    :service/type :service-type/moleben
    :service/date-time (june 17 18)}

   {:service/feast "Sunday 5 after Pentecost"
    :service/type :service-type/vigil
    :service/date-time (july 4 18)}

   {:service/feast "Sunday 5 after Pentecost"
    :service/type :service-type/liturgy
    :service/date-time (july 5 10)}

   {:service/feast "Nativity of St. John the Baptist"
    :service/type :service-type/weekday-evening
    :service/date-time (july 6 18)}

   {:service/feast "Nativity of St. John the Baptist"
    :service/type :service-type/liturgy
    :service/date-time (july 7 7 30)}

   {:service/feast "Virgin Martyr Febronia"
    :service/type :service-type/weekday-evening
    :service/date-time (july 7 18)}

   {:service/feast "Virgin Martyr Febronia"
    :service/type :service-type/liturgy
    :service/date-time (july 8 8)}

   {:service/feast "Moleben & Akathist to the Theotokos"
    :service/type :service-type/moleben
    :service/date-time (july 8 18)}

   {:service/feast "Unmercenaries Cyrus & John"
    :service/type :service-type/weekday-evening
    :service/date-time (july 10 18)}

   {:service/feast "Unmercenaries Cyrus & John"
    :service/type :service-type/liturgy
    :service/date-time (july 11 8)}

   {:service/feast "" ;; ""unknown"" b/c calendar doesn't have corresponding Sunday
    :service/type :service-type/vigil
    :service/date-time (july 11 18)}])

;; todo de-duplicate this with base?
(def expected-basic-sat-sun
  [{:service/feast "Venerable Simeon the Stylite"
    :service/type :service-type/weekday-evening
    :service/all-english? true
    :service/date-time (june 5 18)}

   {:service/feast "Venerable Simeon the Stylite"
    :service/type :service-type/liturgy
    :service/all-english? true
    :service/date-time (june 6 8)}

   {:service/feast "Sunday of All Saints"
    :service/type :service-type/vigil
    :service/date-time (june 6 18)}

   {:service/feast "Sunday of All Saints"
    :service/type :service-type/liturgy
    :service/date-time (june 7 10)}])
