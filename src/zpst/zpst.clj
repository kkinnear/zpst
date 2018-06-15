(ns zpst.zpst
  (:require clojure.string
            clojure.set
            [zpst.zloc :refer [find-fn get-frame-map]]
            [zpst.clean :refer [clean-hooks]]
            [zpst.exception :refer [concat-causes]]
            [zpst.config :refer [add-option-set]]
            [zprint.core :as zp :refer
             [zprint-str czprint-str zprint-fn-str czprint-fn-str czprint
              czprint-fn zprint]]
            [zprint.config :refer [merge-deep remove-key]]
            [zprint.zutil :refer [find-root-and-path-nw find-docstring]]
            [clojure.repl :refer [demunge source-fn stack-element-str]]))

(defn clean-basic
  "Clean things out of a stack trace that we always want to clean out."
  [st]
  (remove #(or (#{"clojure.lang.RestFn" "clojure.lang.AFn"} (.getClassName %))
               (let [split (clojure.string/split (.getClassName %) #"\$")]
                 (or (= "clojure.lang.AFunction" (first split)))))
    st))

(defn zprint-source
  "Call zprint with correct source information if requested."
  [options zloc param-path]
  (let [show (:show options)]
    (when (and (not (empty? (clojure.set/intersection #{:params :name :call
                                                        :docstring :spec}
                                                      show)))
               zloc)
      (let [paths (if (and param-path (:params show)) [param-path] [])
            [root _] (find-root-and-path-nw zloc)
            paths (if (:docstring show)
                    (conj paths
                          (second (find-root-and-path-nw (find-docstring
                                                           root))))
                    paths)]
        #_(def zsr root)
        #_(def zsp paths)
        (if (:call show)
          ; Show the actual call using the focus
          (zprint zloc
                  (merge-deep (:zprint-source options)
                              {:color? (:color? options),
                               :output {:focus {:zloc? true},
                                        :lines (when (:name show) [0]),
                                        :paths paths}}))
          ; Don't show the call, so we use the root zloc and have to get
          ; rid of the focus from the configured options-map
          (let [opt (remove-key
                      (merge-deep (:zprint-source options)
                                  {:zipper? true,
                                   :color? (:color? options),
                                   :output {:focus {},
                                            :lines (when (:name show) [0]),
                                            :paths paths}})
                      [:output :focus])]
            #_(def zso opt)
            (zprint root opt)))))))

(defn zprint-args
  "Given a seq of local params and another seq of actual arguments,
  clean the arguments, pass the entire argument binding vector to
  another function to get any new zprint options, and then use czprint
  to print the ensemble."
  [options locals args]
  (let [clean-args (if (:arg-clean-fn options)
                     (map (:arg-clean-fn options) locals args)
                     args)
        binding-vec (into [] (interleave locals clean-args))
        addtl-zprint-options (when (:arg-vec-options-fn options)
                               ((:arg-vec-options-fn options) binding-vec))
        zprint-options (if addtl-zprint-options
                         (merge-deep (:zprint-args options)
                                     addtl-zprint-options)
                         (:zprint-args options))
        zprint-options (merge-deep zprint-options
                                   {:color? (:color? options),
                                    :vector {:binding? true}})]
    (try
      (zprint binding-vec zprint-options)
      (println)
      (catch Exception e
        (let [root-e (clojure.repl/root-cause e)]
          (println)
          (println "When printing the arguments, encountered this Exception:")
          (println (str (-> root-e
                            class
                            .getSimpleName)
                        " "
                        (.getMessage root-e)))
          (println)
          (println "The locals and their types were:")
          (zprint (into [] (interleave locals (mapv type args))) zprint-options)
          (println)
          #_(println "args:" args))))))

(defn print-frame
  "Given the information about a stack frame, format it for output. Use
  the set of options in :show to decide what to show."
  [{:keys [show], :as options} frame-num fn-name zloc param-path params args]
  (let [separators (not (zero? (count (disj show :stack :frame))))]
    (if separators
      (if (:stack show)
        (printf ">>>>>>>>>> %5d %s\n" frame-num fn-name)
        (println ">>>>>>>>>>" frame-num))
      (if (:stack show) (printf " %5d %s\n" frame-num fn-name) (println)))
    (when (and (:args show) params args)
      (println "Arguments:")
      (zprint-args options params args))
    (zprint-source options zloc param-path)
    (if separators (println "--------"))))

(defn find-zcall
  "Given a string of a function name, find the element of the zcalls
  vector that matches it."
  [class-fn-name zcall-vec]
  #_(println "find-zcall: class-fn-name:" class-fn-name "zcall-vec:" zcall-vec)
  (if-let [zcall (first zcall-vec)]
    (when (= class-fn-name
             (demunge (first (clojure.string/split (str (:fn zcall)) #"\@"))))
      zcall)))

(defn zpst*
  "Actually do zpst functions. Arguments are e and options. The exception in
  e-in could have ex-data, or not."
  [e-in options]
  (let [e-data (concat-causes e-in)
        zcalls (:zcall-vec e-data)
        st (:stacktrace-array e-data)
        ex-str-vec (:exception-str-vec e-data)
        _ (def zst st)
        st-lite (clean-basic st)
        st-lite (if zcalls (clean-hooks st-lite) st-lite)
        _ (def zstl st-lite)
        options (add-option-set options)]
    #_(println "type e-in:" (type e-in)
               "\ntype options:" (type options)
               "\noptions:\n" (czprint-str options))
    #_(println "\n")
    (loop [st-list st-lite
           zcall-vec zcalls
           last-class-fn-name nil
           last-lineno nil
           before-last-class-fn-name nil
           skip? nil
           depth 0
           fn-db []]
      (if-not st-list
        ; Exit, possibly with a list of functions
        (cond (:fns? options) (map :fn-name fn-db)
              (:zdb? options) fn-db
              :else nil)
        ; Process a single frame
        (let [el (first st-list)
              file (.getFileName el)
              class-name (.getClassName el)
              #_(println "File:" file "Classname:" class-name)
              fn-name (.getMethodName el)
              lineno (.getLineNumber el)
              stack-str (stack-element-str el)
              class-fn-name (when (and file
                                       (or (.endsWith file ".clj")
                                           (.endsWith file ".cljc")))
                              (demunge class-name))
              look-for-fn? (and (not skip?)
                                (= last-class-fn-name class-fn-name)
                                (number? last-lineno)
                                (number? lineno)
                                (> last-lineno lineno)
                                class-fn-name)
              ; If we have list of arguments, get this functions args
              ; This only does something if we hooked the functions first
              zcall (if look-for-fn? (find-zcall class-fn-name zcall-vec))
              #_(if class-fn-name
                  (println "class-fn-name:" class-fn-name)
                  (println file fn-name class-fn-name lineno))
              ; is this the same one as the last one but just the
              ; line numbers are different?
              #_(println "fn-db:" (count fn-db) (map :fn-name fn-db))
              print? (and (not (or (:zdb? options)
                                   (:fns? options)
                                   (:fn-num options)))
                          ; Fix this so that it is our frames
                          #_(<= depth (:depth options))
                          (<= (count fn-db) (:depth options)))
              #_(println "print?" print?)
              frame-map (when look-for-fn?
                          #_(println "Look on line" (- last-lineno lineno))
                          (let [source-str (source-fn (symbol class-fn-name))]
                            (when source-str
                              ;(czprint source {:parse-string? true})
                              #_(println "----> found:" class-fn-name)
                              ; Parse the source, and using the resulting zipper
                              ; figure out the call site in the source.
                              (let [[zloc zloc-root]
                                      (find-fn source-str
                                               class-fn-name
                                               (- last-lineno lineno)
                                               before-last-class-fn-name)]
                                #_(println "zloc:" (zprint.zutil/string zloc))
                                (when zloc
                                  ; We found the function and have its zipper
                                  (let [frame-map (when zloc
                                                    ;(and e-info zloc)
                                                    (get-frame-map options
                                                                   zloc
                                                                   zloc-root
                                                                   zcall))
                                        ; build description of this call
                                        frame-map (assoc frame-map
                                                    :fn-name class-fn-name
                                                    :stack-str stack-str
                                                    :index (count fn-db))]
                                    (when print?
                                      (print-frame options
                                                   (count fn-db)
                                                   (:fn-name frame-map)
                                                   zloc
                                                   (:param-path frame-map)
                                                   (:params frame-map)
                                                   (:args frame-map)))
                                    (if (= (:fn-num options) (count fn-db))
                                      (assoc frame-map :fn-num-return? true)
                                      frame-map)))))))]
          ; Print regular stack frame
          ;(when print? (println (str \tab (stack-element-str el))))
          ;(println "clean-args length:" (count (:clean-args frame-map)))
          (when (and print? (:frame (:show options))) (println stack-str))
          (if (:fn-num-return? frame-map)
            frame-map
            (recur (next st-list)
                   (if zcall (next zcall-vec) zcall-vec)
                   (or class-fn-name fn-name)
                   lineno
                   last-class-fn-name
                   (or skip?
                       ;(println "class-name:" class-name)
                       (= class-name "clojure.lang.Compiler"))
                   (inc depth)
                   (if frame-map (conj fn-db frame-map) fn-db))))))))

(defn str-contains?
  "Return true if a string matches any of the strings in regex-set that
  are turned into regexes.  Note that regex-set can simply be a set of 
  strings."
  [regex-set s]
  (let [regex-set (map re-pattern regex-set)] (some #(re-find % s) regex-set)))

(defn epst*
  "Prints a complete stack trace of all nested exceptions, showing nesting
  and the cause chain all the way down. Similar to pst, but shows the whole
  exception structure."
  ([^Throwable e options] (epst* e options 0))
  ([^Throwable e options lvl]
   (binding [*out* *err*]
     (println (str lvl "  Exception:\t" (pr-str (type e))))
     (println (str lvl "  Message:\t" (.getMessage e)))
     ; Useful for debugging exception handling for collect/analyze:
     (when (:dbg? options)
       (println (str lvl "  Exception hash:\t" (.hashCode e)))
       (println (str lvl "  Message length:\t" (count (.getMessage e))))
       (println (str lvl "  Keys in ex-data:\t" (keys (ex-data e)))))
     (let [st (.getStackTrace e)
           cause (.getCause e)]
       (doseq [el (remove #(#{"clojure.lang.RestFn" "clojure.lang.AFn"}
                             (.getClassName %))
                    st)]
         (let [line (str \tab (clojure.repl/stack-element-str el))]
           (if-not (and (:elide? options)
                        (str-contains? (:elide-lines-with options) line))
             (println line))))
       (when cause (println "Caused by:") (epst* cause options (inc lvl)))))))
