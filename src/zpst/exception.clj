(ns zpst.exception
  (:require clojure.string
            clojure.set))

;;
;; # Exception processing
;;

(defn extract-info
  "Given an exception, get the information we are about out
  of it.  Return this in a map with keys:
  [:stacktrace-array :exception :exception-str :zcall-vec]"
  [e]
  #_(println "extract-info: class:" (class e))
  (if-let [data (ex-data e)]
    ; We have a clojure.lang.ExceptionInfo exception, so this has
    ; the :zcalls, and the cause of this exception has the stack.
    {:stacktrace-array nil,
     :exception (class e),
     :exception-str (str (-> e
                             class
                             .getSimpleName)
                         " "
                         (.getMessage e)),
     :zcall-vec (:zcalls data)}
    ; We have a regular java or Clojure exception, not one with extra
    ; data.
    {:stacktrace-array (.getStackTrace e),
     :exception (class e),
     :exception-str (str (-> e
                             class
                             .getSimpleName)
                         " "
                         (.getMessage e)),
     :zcall-vec nil}))

(defn exception-vec
  "Given a single exception, look through it and get all of the
  nested exception information out into a top level vector of
  maps."
  [e]
  (letfn [(f [ex]
             (if-let [cause (.getCause ex)]
               (cons ex (f cause))
               (list ex)))]
    (let [exception-vec (reverse (f e))]
      (doseq [x exception-vec]
        #_(println "\n\nType:" (type x) "\nType Cause:" (type (.getCause x))))
      (into [] (map extract-info exception-vec)))))

(defn sconcat
  "Concatentate a sequence of stacks together (which are actually arrays)."
  [stacktrace-array-seq]
  (let [stacktrace-array-seq (remove nil? stacktrace-array-seq)
        length (apply + (map count stacktrace-array-seq))
        output (make-array java.lang.StackTraceElement length)]
    (loop [array-seq stacktrace-array-seq
           index 0]
      (if-let [stacktrace-array (first array-seq)]
        (do (System/arraycopy stacktrace-array
                              0
                              output
                              index
                              (count stacktrace-array))
            (recur (next array-seq) (+ index (count stacktrace-array))))
        output))))

(defn concat-causes
  "Take an exception and concatenate all of the causes onto one another."
  [e]
  (let [ex-vec (exception-vec e)]
    {:stacktrace-array (sconcat (map :stacktrace-array ex-vec)),
     :exception (vec (map :exception ex-vec)),
     :exception-str-vec (vec (map :exception-str ex-vec)),
     :zcall-vec (into [] (apply concat (map :zcall-vec ex-vec)))}))
