(ns church-calendar-sync.integration.web-app-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [church-calendar-sync.core :as core]))

(deftest page-load-sanity-checks
  (s/check-asserts true) 
  (let [app (core/->app @core/oauth-creds)]
    (testing (str "initial load of " core/main-view-path) 
      (let [response (app {:request-method :get :uri core/main-view-path})]
        (is (=  200 (:status response)))))))