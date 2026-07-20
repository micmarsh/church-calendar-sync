(ns church-calendar-sync.google.gcal
  (:require
   [church-calendar-sync.google.oauth :as oauth]
   [church-calendar-sync.spec :as spec]
   [church-calendar-sync.utils :refer [parse-json]]
   [clojure.spec.alpha :as s]
   [org.httpkit.client :as client]
   [church-calendar-sync.google.oauth.storage :as storage]
   [church-calendar-sync.config-storage :as config])
  (:import
   [java.time ZoneId]))

(s/def ::start-date ::spec/date-time)
(s/def ::end-date ::spec/date-time)
(s/def ::date-range (s/keys :req-un [::start-date ::end-date]))

(def ^:private timezone
  (ZoneId/of "America/Detroit"))

(defn local-dt->rfc3339 [local-date-time]
  (-> local-date-time
      (.atZone timezone)
      (.format java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME)))

(def ^:const base-api
  "https://www.googleapis.com/calendar/v3/")

(defn json [{:keys [access-token token-type] :as token}]
  (s/assert ::oauth/req-auth-parts token)
  {:headers {"Authorization" (str token-type " " access-token)}
   :content-type :json
   :accept :json})

(def ^:private read-resp (comp #(update % :body parse-json) deref))

(defn calendars [token]
  (s/assert ::oauth/req-auth-parts token)
  (-> (str base-api "users/me/calendarList")
      (client/get (json token))
      read-resp))

(defn events
  [calendar-id
   {:keys [start-date end-date] :as params}
   token]
  (s/assert ::date-range params)
  (s/assert ::oauth/req-auth-parts token)
  (-> (str base-api "calendars/" (client/url-encode calendar-id) "/events")
      (client/get (-> (json token)
                      (assoc :query-params {"timeMin" (local-dt->rfc3339 start-date)
                                            "timeMax" (local-dt->rfc3339 end-date)})))
      read-resp))

(def primary-events (partial events "primary"))


(comment
  (def date-range {:start-date (java.time.LocalDateTime/of 2026 6 1 0 0)
                   :end-date (java.time.LocalDateTime/of 2026 6 30  0 0)})

  (def token (church-calendar-sync.google.oauth.storage/get-token church-calendar-sync.core/storage-atom))
  (def cal-id (church-calendar-sync.config-storage/get-config church-calendar-sync.core/storage-atom :church-calendar-sync.app/current-calendar))

  cal-id

  (events (:id cal-id) date-range token)
  
  {:body {:items [
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-21T20:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3011656993660000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=MXY5cmdwN2Y5YWZrYTRpZ20yYWxrbmhrYW9fMjAxNDA3MjFUMjEwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "1v9rgp7f9afka4igm2alknhkao@google.com",
                   :id "1v9rgp7f9afka4igm2alknhkao",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20150126T180000,20170417T170000,20171120T170000,20171218T170000"
                                "RRULE:FREQ=WEEKLY;BYDAY=MO"],
                   :reminders {:use-default true},
                   :sequence 2,
                   :start {:date-time "2014-07-21T17:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Fr. Gregory Office Hours by Appointment: calendly.com/ogrisha ~ о. Григорий принимает (по предварительной договоренности): calendly.com/ogrisha",
                   :updated "2017-09-19T13:41:36.830Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2017-01-11T21:00:00-05:00", :time-zone "America/New_York"},
                   :etag "\"3011656993660000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=Ym8xa2Rvb3VnaXA5bWdkMmp2aGFicmlrYjBfMjAxNzAxMTJUMDAwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "bo1kdoougip9mgd2jvhabrikb0@google.com",
                   :id "bo1kdoougip9mgd2jvhabrikb0",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence
                   ["EXDATE;TZID=America/New_York:20170412T190000,20170419T190000,20170913T190000,20170920T190000,20171206T190000"
                    "RRULE:FREQ=WEEKLY;BYDAY=WE"],
                   :reminders {:use-default true},
                   :sequence 0,
                   :start {:date-time "2017-01-11T19:00:00-05:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Fr. Gregory Office Hours by Appointment: calendly.com/ogrisha ~ о. Григорий принимает (по предварительной договоренности): calendly.com/ogrisha",
                   :updated "2017-09-19T13:41:36.830Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-23T12:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3011656993660000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=ZXA5NWdqaDRsdWljcW9xZmt0ZW1xdjNkcDBfMjAxNDA3MjNUMTMwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "ep95gjh4luicqoqfktemqv3dp0@google.com",
                   :id "ep95gjh4luicqoqfktemqv3dp0",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence
                   ["EXDATE;TZID=America/New_York:20170524T090000,20170712T090000,20170927T090000,20171101T090000,20171108T090000,20171213T090000"
                    "RRULE:FREQ=WEEKLY;BYDAY=WE"],
                   :reminders {:use-default true},
                   :sequence 1,
                   :start {:date-time "2014-07-23T09:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Fr. Gregory Office Hours by Appointment: calendly.com/ogrisha ~ о. Григорий принимает (по предварительной договоренности): calendly.com/ogrisha",
                   :updated "2017-09-19T13:41:36.830Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-09T19:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3011656993660000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=ZXYxcTZ2dGIwdWRjODYxM2k1aWZiNW5tNzhfMjAxNDA3MDlUMjIwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "ev1q6vtb0udc8613i5ifb5nm78@google.com",
                   :id "ev1q6vtb0udc8613i5ifb5nm78",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence
                   ["EXDATE;TZID=America/New_York:20141231T180000,20160106T180000,20170315T180000,20170322T180000,20170329T180000,20170405T180000,20170412T180000,20170419T180000,20170913T180000,20170920T180000,20171206T180000"
                    "RRULE:FREQ=WEEKLY;BYDAY=WE"],
                   :reminders {:use-default true},
                   :sequence 0,
                   :start {:date-time "2014-07-09T18:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Akathist for the Ill ~ Акафист для болящих",
                   :updated "2017-09-19T13:41:36.830Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2016-09-17T17:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3122440311267000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=MzlyMW52bDJ2bTJucDFpODdibWUzOTAxNm9fMjAxNjA5MTdUMTkwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "39r1nvl2vm2np1i87bme39016o@google.com",
                   :id "39r1nvl2vm2np1i87bme39016o",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20170415T150000" "RRULE:FREQ=WEEKLY;BYDAY=SA"],
                   :reminders {:use-default false},
                   :sequence 0,
                   :start {:date-time "2016-09-17T15:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Fr. Gregory Office Hours by Appointment: calendly.com/ogrisha ~ о. Григорий принимает (по предварительной договоренности): calendly.com/ogrisha",
                   :updated "2019-06-22T16:15:55.686Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-12T21:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3122440313323000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=YXUwamlzc3RndDM5bjhzc2h1OW9xZ3E3NzRfMjAxNDA3MTJUMjIwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "au0jisstgt39n8sshu9oqgq774@google.com",
                   :id "au0jisstgt39n8sshu9oqgq774",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20141004T180000,20150411T180000,20160430T180000,20170415T180000"
                                "RRULE:FREQ=WEEKLY;BYDAY=SA"],
                   :reminders {:use-default false},
                   :sequence 0,
                   :start {:date-time "2014-07-12T18:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Vigil ~ Бдение",
                   :updated "2019-06-22T16:15:56.697Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2016-09-24T18:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3122440314998000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=anFxbWRwZ2NtbjRyaWZhamQwNWluazliMDhfMjAxNjA5MjRUMjEwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "jqqmdpgcmn4rifajd05ink9b08@google.com",
                   :id "jqqmdpgcmn4rifajd05ink9b08",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20170415T170000" "RRULE:FREQ=WEEKLY;BYDAY=SA"],
                   :reminders {:use-default false},
                   :sequence 0,
                   :start {:date-time "2016-09-24T17:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Church School -or- Confession: calendly.com/ogrisha ~ Церковная школа -или- Исповедь: calendly.com/ogrisha",
                   :updated "2019-06-22T16:15:57.549Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-06T10:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3122586883607000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=ZG5tbGwyYWZnaTdmajI4cHRhZ2RiYWkwOTBfMjAxNDA3MDZUMTMwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "dnmll2afgi7fj28ptagdbai090@google.com",
                   :id "dnmll2afgi7fj28ptagdbai090",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20150412T090000,20160501T090000,20170416T090000"
                                "RRULE:FREQ=WEEKLY;BYDAY=SU"],
                   :reminders {:use-default false},
                   :sequence 1,
                   :start {:date-time "2014-07-06T09:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary
                   "Confession by Appointment: calendly.com/ogrisha ~ Исповедь (по предварительной договоренности): calendly.com/ogrisha",
                   :updated "2019-06-23T12:37:21.855Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2017-01-01T09:00:00-05:00", :time-zone "America/New_York"},
                   :etag "\"3122586885931000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=ZmdwOTNzOWU5dWwzYmhvODJtYms0bHBmdWtfMjAxNzAxMDFUMTMwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "fgp93s9e9ul3bho82mbk4lpfuk@google.com",
                   :id "fgp93s9e9ul3bho82mbk4lpfuk",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20170416T080000" "RRULE:FREQ=WEEKLY;BYDAY=SU"],
                   :reminders {:use-default false},
                   :sequence 0,
                   :start {:date-time "2017-01-01T08:00:00-05:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Moleben to St. Vladimir ~ Молебен св. Владимиру",
                   :updated "2019-06-23T12:37:23.020Z"}
                  {:created "2017-09-19T13:34:34.000Z",
                   :creator {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :end {:date-time "2014-07-06T13:00:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3122586887742000\"",
                   :event-type "default",
                   :html-link
                   "https://www.google.com/calendar/event?eid=cG5rZ29yMTQ5Ymg2ZzJtNTM3a2F1amxhZm9fMjAxNDA3MDZUMTQwMDAwWiBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid "pnkgor149bh6g2m537kaujlafo@google.com",
                   :id "pnkgor149bh6g2m537kaujlafo",
                   :kind "calendar#event",
                   :organizer {:display-name "Michael Marsh", :email "michael.marsh42@gmail.com", :self true},
                   :recurrence ["EXDATE;TZID=America/New_York:20150412T100000,20160501T100000,20170416T100000"
                                "RRULE:FREQ=WEEKLY;BYDAY=SU"],
                   :reminders {:use-default false},
                   :sequence 0,
                   :start {:date-time "2014-07-06T10:00:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Sunday Divine Liturgy ~ Воскресная Божественная Литургия",
                   :updated "2019-06-23T12:37:23.906Z"}
                  {:attendees [{:email "michael.marsh42@gmail.com", :response-status "accepted", :self true}
                               {:display-name "Brent Brehm", :email "bbrehm@doubleasolutions.net", :response-status "needsAction"}
                               {:display-name "Kris Charatonik",
                                :email "kcharatonik@doubleasolutions.net",
                                :response-status "needsAction"} {:email "mike@marsh.pw", :response-status "needsAction"}
                               {:display-name "Wylie Harris",
                                :email "wharris@doubleasolutions.net",
                                :organizer true,
                                :response-status "accepted"}],
                   :created "2026-06-02T14:13:19.000Z",
                   :creator {:email "michael.marsh42@gmail.com", :self true},
                   :description
                   "Thank you for your interest in employment at Double A Solutions.\n\nWe are excited to schedule your Interview with the hiring leaders. We look forward to meeting you for your Teams Interview on Tuesday, June 2, 2026, at 10:30 am EST.\n\nInterviewer Panel:\nBrent - Chief Architect\nKris - Software Developer\nWylie - Recruiter & Retention Specialist\n\nPlease let us know if you have any questions in the meantime! We look forward to meeting with you!\nThank you!\n\n\n________________________________________________________________________________\nMicrosoft Teams meeting\nJoin: https://teams.microsoft.com/meet/219137232612292?p=lu3d1SdNShj6ak9H0S\nMeeting ID: 219 137 232 612 292\nPasscode: hb9VA7oG\n________________________________\nNeed help?<https://aka.ms/JoinTeamsMeeting?omkt=en-US> | System reference<https://teams.microsoft.com/l/meetup-join/19%3ameeting_Njg4MWVhYjAtYWY4Yi00Yjg0LWE2ZDUtZGZmYTI1NzFhYzMz%40thread.v2/0?context=%7b%22Tid%22%3a%2232c6383d-f364-4f17-ac2c-39a51ba5f674%22%2c%22Oid%22%3a%22296c5072-f546-4f73-b17d-d748d4f317a5%22%7d>\nFor organizers: Meeting options<https://teams.microsoft.com/meetingOptions/?organizerId=296c5072-f546-4f73-b17d-d748d4f317a5&tenantId=32c6383d-f364-4f17-ac2c-39a51ba5f674&threadId=19_meeting_Njg4MWVhYjAtYWY4Yi00Yjg0LWE2ZDUtZGZmYTI1NzFhYzMz@thread.v2&messageId=0&language=en-US>\nDouble A Solutions\nPrivacy and security<https://doubleasolutions.net>\n________________________________________________________________________________\n",
                   :end {:date-time "2026-06-02T11:30:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3560819200828542\"",
                   :event-type "default",
                   :guests-can-invite-others false,
                   :html-link
                   "https://www.google.com/calendar/event?eid=XzYwcTMwYzFnNjBvMzBlMWk2MG80YWMxZzYwcmo4Z3BsODhyajJjMWg4NHMzNGg5ZzYwczMwYzFnNjBvMzBjMWc4Z280NGhhNThoMWowY2htOGwyNDhncGc2NG8zMGMxZzYwbzMwYzFnNjBvMzBjMWc2MG8zMmMxZzYwbzMwYzFnOGtwamVkOWg2b3FqOGNoazc0cDM4Z2hrNjEwajZoMW42MHI0Y2QxbjhsMmo4ZDFoOGQyZyBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid
                   "040000008200E00074C5B7101A82E00800000000D0BEEDC026EDDC01000000000000000010000000E375165424924B40A3D706F47EE441CE",
                   :id
                   "_60q30c1g60o30e1i60o4ac1g60rj8gpl88rj2c1h84s34h9g60s30c1g60o30c1g8go44ha58h1j0chm8l248gpg64o30c1g60o30c1g60o30c1g60o32c1g60o30c1g8kpjed9h6oqj8chk74p38ghk610j6h1n60r4cd1n8l2j8d1h8d2g",
                   :kind "calendar#event",
                   :location "Microsoft Teams Meeting",
                   :organizer {:display-name "Wylie Harris", :email "wharris@doubleasolutions.net"},
                   :private-copy true,
                   :reminders {:use-default true},
                   :sequence 2,
                   :start {:date-time "2026-06-02T10:30:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Michael Marsh; Software Developer - Teams Interview",
                   :updated "2026-06-02T14:13:20.414Z"}
                  {:attendees [{:email "michael.marsh42@gmail.com", :response-status "accepted", :self true}
                               {:display-name "Jacob Lindhurst",
                                :email "jacob.lindhurst@gprsinc.com",
                                :organizer true,
                                :response-status "accepted"} {:email "mike@marsh.pw", :response-status "needsAction"}],
                   :created "2026-06-03T14:04:59.000Z",
                   :creator {:email "michael.marsh42@gmail.com", :self true},
                   :description
                   "\n________________________________________________________________________________\nMicrosoft Teams meeting\nJoin: https://teams.microsoft.com/meet/267982779862948?p=45nmOKlVgnN6PDPbKX\nMeeting ID: 267 982 779 862 948\nPasscode: Px9sb9E2\n________________________________\nNeed help?<https://aka.ms/JoinTeamsMeeting?omkt=en-US> | System reference<https://teams.microsoft.com/l/meetup-join/19%3ameeting_MjU5MjBmMDctNDJmZC00OThhLWIyNTMtMjFjNzU5YmNlYmQ5%40thread.v2/0?context=%7b%22Tid%22%3a%22c9951b4f-f290-44ac-a562-ccf85b3b11b7%22%2c%22Oid%22%3a%225a6a6c82-874b-4ecd-8323-49c21724b6b7%22%7d>\nDial in by phone\n+1 567-249-1957,,400968124#<tel:+15672491957,,400968124#> United States, Toledo\nFind a local number<https://dialin.teams.microsoft.com/ac4ec2c7-0381-4449-b0bf-2d59deaba6ce?id=400968124>\nPhone conference ID: 400 968 124#\nFor organizers: Meeting options<https://teams.microsoft.com/meetingOptions/?organizerId=5a6a6c82-874b-4ecd-8323-49c21724b6b7&tenantId=c9951b4f-f290-44ac-a562-ccf85b3b11b7&threadId=19_meeting_MjU5MjBmMDctNDJmZC00OThhLWIyNTMtMjFjNzU5YmNlYmQ5@thread.v2&messageId=0&language=en-US> | Reset dial-in PIN<https://dialin.teams.microsoft.com/usp/pstnconferencing>\n________________________________________________________________________________\n",
                   :end {:date-time "2026-06-03T10:45:00-04:00", :time-zone "America/New_York"},
                   :etag "\"3560990999821726\"",
                   :event-type "default",
                   :guests-can-invite-others false,
                   :html-link
                   "https://www.google.com/calendar/event?eid=XzYwcTMwYzFnNjBvMzBlMWk2MG80YWMxZzYwcmo4Z3BsODhyajJjMWg4NHMzNGg5ZzYwczMwYzFnNjBvMzBjMWc3MTJqNmRwbTZvcDQ0aGkyOG9wNDhncGc2NG8zMGMxZzYwbzMwYzFnNjBvMzBjMWc2MG8zMmMxZzYwbzMwYzFnODkzM2NkMjE3MHBqNGVhMTcxMmo0YzFrOGgwajJoOW82ZDIzY2Q5azZkMTNnZGkzNmgwZyBtaWNoYWVsLm1hcnNoNDJAbQ",
                   :i-cal-uid
                   "040000008200E00074C5B7101A82E008000000008E37662BFBF2DC01000000000000000010000000BF64A8329A8E204DA1E83D6543B86C4A",
                   :id
                   "_60q30c1g60o30e1i60o4ac1g60rj8gpl88rj2c1h84s34h9g60s30c1g60o30c1g712j6dpm6op44hi28op48gpg64o30c1g60o30c1g60o30c1g60o32c1g60o30c1g8933cd2170pj4ea1712j4c1k8h0j2h9o6d23cd9k6d13gdi36h0g",
                   :kind "calendar#event",
                   :location "Microsoft Teams Meeting",
                   :organizer {:display-name "Jacob Lindhurst", :email "jacob.lindhurst@gprsinc.com"},
                   :private-copy true,
                   :reminders {:use-default true},
                   :sequence 1,
                   :start {:date-time "2026-06-03T10:30:00-04:00", :time-zone "America/New_York"},
                   :status "confirmed",
                   :summary "Michael Marsh (Sr. Backend Developer) - Interview",
                   :updated "2026-06-03T14:04:59.910Z"}
  ]
          }}

  )
