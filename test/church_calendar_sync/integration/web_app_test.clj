(ns church-calendar-sync.integration.web-app-test
  (:require [church-calendar-sync.app :as app]
            [church-calendar-sync.core :as core]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]))

(deftest page-load-sanity-checks
  (s/check-asserts true) 
  (let [app (core/->app @core/oauth-creds)]
    (testing (str "initial load of " app/main-view-path) 
      (let [response (app {:request-method :get :uri app/main-view-path})]
        (is (=  200 (:status response)))))))