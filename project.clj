(defproject memobook "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [figwheel "0.2.3-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.3-SNAPSHOT"]
            [lein-marginalia "0.8.0"]]

  :hooks [leiningen.cljsbuild]

  :clean-targets ^{:protect false} ["dev-resources/public/js/compiled"
                                    "resources/public/js/compiled"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src-cljs" "src-cljs-dev"]
              :compiler {:output-to "dev-resources/public/js/compiled/memobook.js"
                         :output-dir "dev-resources/public/js/compiled/out"
                         :asset-path "js/compiled/out"
                         :main memobook.dev
                         :optimizations :none
                         :source-map true}}
             {:id "release"
              :source-paths ["src-cljs"]
              :compiler {:output-to "resources/public/js/compiled/memobook.js"
                         :main memobook.core
                         :optimizations :advanced
                         :externs ["externs.js"]
                         :pretty-print false}}]})
