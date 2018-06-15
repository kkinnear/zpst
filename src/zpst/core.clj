(ns zpst.core
  (:require [zpst.hook :refer [hook unhook]]
            [zpst.zpst :refer [zpst* epst* print-frame]]
            [zpst.config :refer
             [get-options config-set-options! get-explained-options
              convert-keys-to-set validate-incoming-options]]
            [zpst.spec :refer [validate-basic]]
            [zprint.core :as zp :refer
             [zprint-str czprint-str zprint-fn-str czprint-fn-str czprint
              czprint-fn]]
            [zprint.config :refer [merge-deep]]))


;;
;; # Database of arguments and calls
;;

;;
;; Database of everything know from hooking and reproducing an exception
;;

(defonce zdb (atom []))

;;
;; Current frame from zdb on which commands are operating
;;

(defonce zframe-num (atom 0))

;;
;; Return from the last successful (re-eval)
;;

(defonce zre-eval-return (atom nil))

;;
;; # User Visible API
;;

;;
;; ## Configuration
;;

(defn set-options!
  "Change current options."
  [options]
  (config-set-options! options))

(defn validate-and-merge-options
  "Validate the option map of a subset of zpst and merge it into
  the existing option-map for that subcomponent.  The subset is a
  keyword, the subset-string is a description for any errors.  Throw
  a useful exception if they don't validate."
  [subset subset-string options]
  (when options (validate-incoming-options subset-string {subset options}))
  (merge-deep (subset (get-options)) options))

;;
;; ## Show source code for any Exception
;;

(defn zpst
  "Show the code and arguments of a stack trace. 
  Three possible arguments, x y and z. Possible choices are:

  [] use *e and default options

  [x] x is number, use it for depth
      x is map, it is options map
      x is neither, assume it is exception

  [x y] x is number, use it for depth, y must be options map
        y is number, use it for depth, x must be exception
        y is map, use it as options map, x must be exception
          This is the entry into the actual zpst operation

  [x y z] y is number and z is map, y is depth, z options map, x exception

  All other values are errors."
  ([] (zpst *e {}))
  ([x]
   (cond (number? x) (zpst *e {:depth x})
         (map? x) (zpst *e x)
         (keyword? x) (cond (= x :explain) (czprint (get-explained-options))
                            (= x :help) (println zpst.config/help-str)
                            :else (println "Didn't understand:" x))
         :else (zpst x {})))
  ; This is the reall call to zpst*
  ([x y]
   (cond (and (number? x) (map? y)) (zpst *e (merge-deep y {:depth x}))
         (number? y) (zpst x {:depth y})
         ; This is the entry point where we actually merge with the
         ; default options and call the function that really does the work.
         (map? y) (zpst* x
                         #_(merge-deep (:zpst (get-options)) y)
                         (validate-and-merge-options :zpst "zpst" y))
         :else (println "Second argument must be a number or a map!")))
  ([x y z]
   (cond
     (and (number? y) (map? z)) (zpst x (merge-deep z {:depth y}))
     :else
       (println
         "Second and third arguments must be
                                   a number and a map!"))))


(defn epst
  "Prints a complete stack trace of all nested (chained) exceptions,
  showing nesting and the cause chain all the way down. Similar to
  pst, but shows the whole exception structure.  By default elides
  some stack trace lines that come from hooking functions.  Use
  {:elide? false} to disable this."
  ([e options]
   (epst* e
          (validate-and-merge-options :epst "epst" options)
          #_(merge-deep (:epst (get-options)) options)))
  ([e-or-options]
   (if (map? e-or-options) (epst *e e-or-options) (epst e-or-options nil)))
  ([] (epst *e nil)))

;;
;; ## Setup and Collect information from Exception
;;

(defn clear
  "Empty the database and frame pointer into the database."
  []
  (reset! zdb [])
  (reset! zframe-num 0)
  (when-not (empty? @zpst.hook/hooked-fns)
    (println "Unhooking previously hooked functions:")
    (unhook))
  nil)

(defn collect
  "This will scan the stack trace from an exception and find all of the
  functions where we can see the source code -- which are, presumabely,
  the functions for which we may want to examine the inputs and outputs.
  You can also specify the namespaces that you want information collected
  for here (as well as in set-options!)."
  ([e options]
   (clear)
   (hook (validate-and-merge-options :collect "collect" options) e))
  ([] (collect *e nil))
  ([e-or-options]
   (if (map? e-or-options)
     (collect *e e-or-options)
     (collect e-or-options nil))))

(defn analyze
  "After collect has been called, and the exception has been recreated,
  there is a lot of information held in the exception(s).  Extract that
  information from the exception(s) and put it into the zdb internal
  database."
  ([e]
   (unhook)
   (println "Replacing information on"
            (count @zdb)
            "frames in current frame database")
   (reset! zdb (zpst* e (:analyze (get-options))))
   ; Don't let the value of zdb every leak out of here, as it can't be printed
   ; at the repl!
   (println "Loaded frame database with information for" (count @zdb) "frames")
   nil)
  ([] (analyze *e)))

;;
;; ## Examine current frame and database
;;

(defn check-frame
  "Given a frame number, check to see that it is in range."
  [n]
  (if (and (< n (count @zdb)) (not (neg? n)))
    n
    (do (println "There is no frame" n "in the database.")
        (println "Valid frame numbers range from 0 to" (dec (count @zdb)))
        nil)))

(defn get-frame
  "Given a frame number, get this frame from zdb."
  ([n] (when (check-frame n) (nth @zdb n)))
  ([] (get-frame @zframe-num)))

(defn set-frame!
  "Given a number and a database, create an environment
  to examine and reproduce a particular call."
  [n]
  (when (check-frame n)
    (reset! zframe-num n)
    (println "Setting current frame to:" n (:fn-name (get-frame)))))

(defn show
  "Show information about frames.  Takes things to show: :args :params
  :source :stack, then zero to two numbers, and then an optional options
  map."
  [& rest]
  (let [[options rest]
          (if (map? (last rest)) [(last rest) (butlast rest)] [nil rest])
        [n m rest] (cond
                     (and (number? (last rest))
                          (> (count rest) 1)
                          (number? (nth rest (- (count rest) 2))))
                       [(nth rest (- (count rest) 2)) (last rest)
                        (take (- (count rest) 2) rest)]
                     (number? (last rest)) [(last rest) (last rest)
                                            (take (dec (count rest)) rest)]
                     (and (keyword? (last rest)) (= :all (last rest)))
                       [0 (count @zdb) (butlast rest)]
                     :else [@zframe-num @zframe-num rest])
        command-set (set rest)
        options (validate-and-merge-options :commands "show command" options)
        command-set (if (empty? command-set)
                      (convert-keys-to-set zpst.config/display-keys options)
                      command-set)
        options (assoc options :show command-set)]
    #_(println "command-set:" command-set "[n m]:" [n m] "options:" options)
    (doseq [i (range (max n 0) (min (inc m) (count @zdb)))]
      (let [{:keys [params clean-args args zloc param-path fn-name stack-str],
             :as frame}
              (nth @zdb i)]
        (print-frame options i fn-name zloc param-path params args)
        #_(print-frame-command options
                               i
                               fn-name
                               stack-str
                               zloc
                               param-path
                               params
                               args)))))

;;
;; ## Argument Manipulation
;;

(defn find-in-seq
  "Given something, find its location in a seq."
  [coll x]
  (let [n (reduce #(if (= %2 x) (reduced %1) (inc %1)) 0 coll)]
    (when (< n (count coll)) n)))

(defn get-arg-number
  "Given a number, symbol, or string name, find the number of this argument
  in the args vector."
  [name-or-n params frame-num]
  (let [n (cond (number? name-or-n) name-or-n
                :else (let [param-name (if (string? name-or-n)
                                         (symbol name-or-n)
                                         name-or-n)]
                        (find-in-seq params param-name)))]
    (cond
      (nil? n) (throw (Exception. (str "Could not locate "
                                       name-or-n
                                       " in the parameters for frame "
                                       frame-num
                                       "!")))
      (>= n (count params))
        (throw
          (Exception. (str "Argument "
                           n
                           " does not fall within the parameters for frame "
                           frame-num
                           "!")))
      :else n)))

(defn get-arg
  "Get an argument from the current environment.  Accepts either a number
  where 0 is the first argument, or a name."
  [name-or-n]
  (let [{:keys [args index params fn-name thread-bindings env-map], :as frame}
          (get-frame)
        n (get-arg-number name-or-n params index)]
    (nth args n)))

(defn replace-frame!
  "Set a new frame in the database, being very careful to not
  return the results of the swap!"
  [n new-frame]
  (swap! zdb assoc n new-frame)
  nil)

(defn set-arg!
  "Change the argument specified by a name, number, or string."
  [name-or-n v]
  (let [{:keys [args args-orig index params fn-name thread-bindings env-map],
         :as frame}
          (get-frame)
        n (get-arg-number name-or-n params index)
        args (into [] args)
        new-args-orig (when-not args-orig args)
        new-args (assoc args n v)
        new-frame
          (if new-args-orig (assoc frame :args-orig new-args-orig) frame)
        new-frame (assoc new-frame :args new-args)]
    (replace-frame! @zframe-num new-frame)))

(defn restore-arg!
  "Set an argument that has been changed back to the original value."
  [name-or-n]
  (let [{:keys [args args-orig params fn-name index thread-bindings env-map],
         :as frame}
          (get-frame)
        n (get-arg-number name-or-n params index)
        args (into [] args)]
    (if (and args-orig (not= (nth args n) (nth args-orig n)))
      (let [new-args (assoc args n (nth args-orig n))
            new-frame (assoc frame :args new-args)]
        (replace-frame! @zframe-num new-frame))
      (println "Argument n has not been changed!"))))

;;
;; ## Reproduce a call with current arguments
;;

(defn re-eval
  "Recreate a call just like the one at function n.  If no value
  is given, use the information from zframe."
  ([] (re-eval @zframe-num))
  ([n]
   (let [{:keys [args params fn-name thread-bindings env-map], :as frame}
           (get-frame n)]
     (reset! zre-eval-return
             (when frame
               (with-redefs-fn env-map
                 (fn []
                   (with-bindings* thread-bindings
                     (fn [] (apply (resolve (symbol fn-name)) args)))))))
     nil)))

(defn get-re-eval-return
  "Return whatever re-eval returned on its last invocation."
  []
  @zre-eval-return)
