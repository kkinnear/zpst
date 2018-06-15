;!zprint {:vector {:wrap? false}}
(defproject zpst "0.1.3"
  :description "Output useful information from stack backtraces."
  :url "https://github.com/kkinnear/zpst"
  :license {:name "MIT License",
            :url "https://opensource.org/licenses/MIT",
            :key "mit",
            :year 2015}
  :plugins [[lein-expectations "0.0.8"] [lein-zprint "0.3.9"]]
  :profiles {:dev {:dependencies [[expectations "2.2.0-rc1"]
                                  [com.taoensso/tufte "1.1.1"]]}}
  :zprint {:old? false}
  :jar-exclusions [#"\.(java|cljs|txt)"]
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-Xss500m"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 #_[org.clojure/clojure "1.8.0"] 
		 [zprint "0.4.10"]
                 #_[clojure-future-spec "1.9.0-alpha17"] 
		 [robert/hooke "1.3.0"]])
