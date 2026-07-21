(ns church-calendar-sync.unit.calendar-sync-helpers 
  (:require [church-calendar-sync.app.processing-upload :refer [matches?
                                                                gcal-event-index
                                                                service->gcal-events]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]))

(def service->gcal-events'
  (partial service->gcal-events 
   (gcal-event-index
    [{:end {:date-time "2014-07-09T19:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2014-07-09T18:00:00-04:00", :time-zone "America/New_York"},
      :summary "Akathist for the Ill ~ Акафист для болящих"}
     {:end {:date-time "2016-09-17T17:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2016-09-17T15:00:00-04:00", :time-zone "America/New_York"},
      :summary "Fr. Gregory Office Hours by Appointment"}
     {:end {:date-time "2014-07-12T21:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2014-07-12T18:00:00-04:00", :time-zone "America/New_York"},
      :summary "Vigil ~ Бдение"}
     {:end {:date-time "2016-09-24T18:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2016-09-24T17:00:00-04:00", :time-zone "America/New_York"},
      :summary "Church School -or- Confession ~ Церковная школа -или- Исповедь: calendly.com/ogrisha"}
     {:end {:date-time "2014-07-06T10:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2014-07-06T09:00:00-04:00", :time-zone "America/New_York"},
      :summary "Confession by Appointment ~ Исповедь (по предварительной договоренности): calendly.com/ogrisha"}
     {:end {:date-time "2017-01-01T09:00:00-05:00", :time-zone "America/New_York"},
      :start {:date-time "2017-01-01T08:00:00-05:00", :time-zone "America/New_York"},
      :summary "Moleben to St. Vladimir ~ Молебен св. Владимиру"}
     {:end {:date-time "2014-07-06T13:00:00-04:00", :time-zone "America/New_York"},
      :start {:date-time "2014-07-06T10:00:00-04:00", :time-zone "America/New_York"},
      :summary "Sunday Divine Liturgy ~ Воскресная Божественная Литургия"}])))

(deftest test-single-service-gcal-events
  (s/check-asserts true)
  (testing "basic mapping of missing service"
    (is (= [{:start {:date-time "2018-09-16T18:00:00-04:00", :time-zone "America/New_York"},
             :end {:date-time "2018-09-16T20:00:00-04:00", :time-zone "America/New_York"},
             :summary "Evening Services"}]
           (service->gcal-events' {:event-type :event-type/service
                                   :service/type :service-type/weekday-evening
                                   :event/date-time (java.time.LocalDateTime/of 2018 9 16 18 0)
                                   :service/feast "Holy Prophet Moses"}))))
  (testing "create all-day feast for liturgy"
    (is (= [{:start {:date-time "2018-09-17T08:00:00-04:00", :time-zone "America/New_York"} 
             :end {:date-time "2018-09-17T10:00:00-04:00", :time-zone "America/New_York"},
             :summary "Div. Liturgy"}
            {:end {:date "2018-09-17"},
             :start {:date "2018-09-17"},
             :summary "Holy Prophet Moses"}]
           (service->gcal-events' {:event-type :event-type/service
                                   :service/type :service-type/liturgy
                                   :event/date-time (java.time.LocalDateTime/of 2018 9 17 8 0)
                                   :service/feast "Holy Prophet Moses"}))))

  (testing "skips existing events"
    (is (empty?
         (service->gcal-events' {:event-type :event-type/service
                                 :service/type :service-type/vigil
                                 :event/date-time (java.time.LocalDateTime/of 2014 7 12 18 0)})))))

(deftest test-matches?
  (s/check-asserts true)
  (testing "basic match case"
    (is (matches? {:event-type :event-type/service
                   :service/type :service-type/liturgy
                   :event/date-time (java.time.LocalDateTime/of 2014 7 6 10 0)
                   :service/feast "Sunday ?? After Pentecost"}
                  {:end {:date-time "2014-07-06T13:00:00-04:00", :time-zone "America/New_York"},
                   :start {:date-time "2014-07-06T10:00:00-04:00", :time-zone "America/New_York"},
                   :summary "Sunday Divine Liturgy ~ Воскресная Божественная Литургия"}))))

(comment
  ( = (java.time.LocalDateTime/of 2014 7 6 10 0)
   (.toLocalDateTime
    (church-calendar-sync.app.processing-upload/->date-time "2014-07-06T10:00:00-04:00")))
  
  )