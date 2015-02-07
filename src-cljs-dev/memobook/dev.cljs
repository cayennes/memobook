(ns memobook.dev
  (:require
    [memobook.core]
    [figwheel.client :as fw]))

(enable-console-print!)

(fw/start
  {:on-jsload (fn [] (print "reloaded"))
   :build-id "dev"})
