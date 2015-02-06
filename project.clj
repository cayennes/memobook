(defproject memobook "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [om "0.7.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [figwheel "0.1.5-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.5-SNAPSHOT"]
            [com.cemerick/austin "0.1.5"]
            [lein-marginalia "0.8.0"]]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src-cljs" "src-cljs-dev"]
              :compiler {:output-to "dev-resources/public/js/compiled/memobook.js"
                         :output-dir "dev-resources/public/js/compiled/out"
                         :optimizations :none
                         :source-map true
                         :pretty-print true}}
             {:id "release"
              :source-paths ["src-cljs"]
              :compiler {:output-to "resources/public/js/compiled/memobook.js"
                         :output-dir "resources/public/js/compiled/out"
                         :optimizations :advanced
                         :preamble ["react/react.min.js"]
                         :externs ["externs.js"
                                   "react/externs/react.js"]}}]})
