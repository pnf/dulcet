(defproject reagent-test "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [reagent "0.4.3"]
                 [reagent-utils "0.1.0"]
                 [secretary "1.2.0"]
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]
                 [http-kit "2.1.18"] ;; !!!
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/sente "1.2.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [com.cognitect/transit-clj  "0.8.259"]
                 [com.cognitect/transit-cljs "0.8.194"]
                 [ring "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [prone "0.6.0"]
                 [compojure "1.2.0"]
                 [selmer "0.7.2"]
                 [environ "1.0.0"]
                 [leiningen "2.5.0"]
                 [figwheel "0.1.5-SNAPSHOT"]
                 [prone "0.6.0"]]

  :plugins [
            [lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]
            [lein-ring "0.8.13"]
            [lein-asset-minifier "0.2.0"]]

  :main reagent-test.handler

  :ring {:handler reagent-test.handler/app}

  :min-lein-version "2.5.0"

  :uberjar-name "reagent-test.jar"

  :minify-assets
  {:assets
    {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}

  ;; Call `lein start-dev` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"build-once" ["do" "cljx" "once," "cljsbuild" "once"]
   "start-dev"  ["repl" ":headless"]}

  :profiles {:dev {:repl-options {:init-ns reagent-test.handler
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.3.1"]
                                  [pjstadig/humane-test-output "0.6.0"]]

                   :plugins [[lein-figwheel "0.1.4-SNAPSHOT"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              :ring-handler reagent-test.handler/app
                              }

                   :env {:dev? true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                             {:source-paths ["env/prod/cljs"]
                                              :compiler
                                              {:optimizations :advanced
                                               :pretty-print false}}}}}

             :production {:ring {:open-browser? false
                                 :stacktraces?  false
                                 :auto-reload?  false}}})
