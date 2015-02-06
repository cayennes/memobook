(ns memobook-dev.core
  (:require [figwheel.client :include-macros true]
            [clojure.browser.repl :as repl]))

(enable-console-print!)

;; from http://astashov.github.io/blog/2014/07/30/perfect-clojurescript-development-environment-with-vim/

(figwheel.client/watch-and-reload)

(repl/connect "http://localhost:9000/repl")

;; from vim:
;; :Piggieback 9000

;; from nrepl:
;; => (require 'cljs.repl.browser)
;; => (cemerick.piggieback/cljs-repl :repl-env (cljs.repl.browser/repl-env :port 9000))
