(ns zpst.zloc
  (:require clojure.string
            clojure.set
            [zprint.zutil]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

;;
;; # zloc Utilities
;;

(defn count-newlines
  "Given a string, count the newlines in the string."
  [s]
  (dec (count (clojure.string/split (str s "x") #"\n"))))

(defn newlines
  "If the zloc contains newlines, return the count, otherwise return
  0. [<count> zloc] where zloc is nil if there isn't anything"
  [zloc]
  (cond (z/linebreak? zloc) [1 nil]
        (or (= (z/tag zloc) :comment) (= (z/tag zloc) :multi-line))
          [(count-newlines (z/string zloc)) zloc]
        :else [0 nil]))

(defn linen-one
  "Return the elements of a single line in a vector of zlocs."
  [zloc n]
  (loop [zloc zloc
         i 0
         zline []]
    #_(prn (str "zloc: '"
                (z/string zloc)
                "' i: "
                i
                " zline: '"
                (apply str (map z/string zline))
                "'"))
    (if (z/end? zloc)
      (when (= n i) zline)
      (if zloc
        (if (z/down* zloc)
          (recur (z/down* zloc) i zline)
          (let [[nl zl] (newlines zloc)]
            (if (not (zero? nl))
              (if (= n i)
                (if zl (conj zline zl) zline)
                (recur (z/next* zloc) (+ nl i) []))
              (recur (z/next* zloc) i (conj zline zloc)))))
        (if (z/up* zloc)
          (recur (z/up* zloc) i (conj zline zloc))
          (println "Total lines:" i))))))

(defn fn-zloc?
  "Try and determine if this zloc points to a function.  Criteria:
  the meta data for the symbol exists and has :arglists and the zloc
  is at the left end of a list."
  [zloc]
  (and zloc
       (not (zprint.zutil/z-coll? zloc))
       ; This next line leave out places where functions are passed into other
       ; functions as arguments.
       ; Doesn't cause an explosion if we leave it out, and it picks up the
       ; rather critical with-redefs-fn.  Which we *could* special case if
       ; we needed to in the caller to this (probably not here).  But maybe we
       ; don't need to special case it, and maybe this give us better stuff
       ; overall.
       ;     (:arglists (meta (resolve (symbol (zprint.zutil/string zloc)))))
       (not (zprint.zutil/zleftnws zloc))
       (= (zprint.zutil/tag (zprint.zutil/up* zloc)) :list)))

(defn match-fn
  "Given two strings, see if the function names match.  Tries basic =
  and also tries to remove the namespace if it appears."
  [fn1 fn2]
  #_(println "fn1:" fn1 "fn2" fn2)
  (and fn1
       fn2
       (or (= fn1 fn2)
           (= (last (clojure.string/split fn1 #"/"))
              (last (clojure.string/split fn2 #"/"))))))

(defn find-fn
  "Given a fn-name and a line number in that function, find
  fn-name-on-line on that line and return the zloc which invokes
  it and the root of the zipper, as [zloc zloc-root].  
  If this can't be found, return nil. Both fn-names are strings."
  [source-str fn-name lineno fn-name-on-line]
  #_(println "find-fn: fn-name:" fn-name
             "lineno:" lineno
             "fn-name-on-line:" fn-name-on-line)
  (when source-str
    (let [ps (p/parse-string source-str)
          zip (z/edn* ps)
          #_(println "zip:" (zprint.zutil/string zip))
          line-vec (linen-one zip lineno)
          line-vec (remove zprint.zutil/whitespace? line-vec)
          #_(println "line-vec" (map zprint.zutil/string line-vec))
          fn-vec (filter fn-zloc? line-vec)
          #_(println "fn-vec:" (map zprint.zutil/string fn-vec))
          match-vec (map #(if (match-fn fn-name-on-line (zprint.zutil/string %))
                           %)
                      fn-vec)
          match-vec (remove nil? match-vec)
          #_(println "match-vec:" (map zprint.zutil/string match-vec))
          match-len (count match-vec)
          fn-len (count fn-vec)
          #_(println "match-len:" match-len "fn-len:" fn-len)]
      (cond (not (zero? match-len)) [(zprint.zutil/up* (first match-vec)) zip]
            ; Well, if we only have one function, assume that's the one
            (= fn-len 1) [(zprint.zutil/up* (first fn-vec)) zip]
            ; We might want to get smarter here, if we have multiple
            ; fns maybe we should figure out a way to pick one over the
            ; others?
            (> fn-len 1) [(zprint.zutil/up* (first fn-vec)) zip]
            :else nil))))

;;
;; ## Find Arguments
;;

(defn follow-pathx
  "Given a zloc and a path, follow the path for n levels."
  [zloc-root path-seq n]
  (loop [zloc zloc-root
         path (take n path-seq)
         path-step 0]
    (if (empty? path)
      zloc
      (do #_(println "follow-pathx: path-step:" path-step
                     "path:" path
                     "zloc:" (zprint.zutil/string zloc))
          (if (zero? path-step)
            (recur (zprint.zutil/down* zloc) (next path) (first path))
            (recur (zprint.zutil/zrightnws zloc) path (dec path-step)))))))

(defn find-args-from-focus
  "Given a zloc of a section of a function on which to focus, work
  back up the function and return the argument vector."
  [zloc]
  (def faff zloc)
  (let [[zloc-root path] (zprint.zutil/find-root-and-path-nw zloc)
        zloc-args (follow-pathx zloc-root path 2)]
    (when (= (zprint.zutil/tag zloc-args) :vector) zloc-args)))

(defn find-args
  "Given a zloc of the top of a function, do what we can to find
  the zloc of the args. Return nil if we can't."
  [zloc-focus zloc-root]
  ; There are three possibilities here:
  ; 1. defn with a single argument vector
  ; 2. defn with multiple-arity forms
  ; 3. def with a meta containing arglists
  ; or, of course, I suppose def with no arglists.
  ;
  ; For 1 we just look for the first vector at the top level.  If
  ; we don't find one, but find a list first, then we assume it is
  ; 2.  For two, we find the path to the thing we are going to focus
  ; on (which is zloc), and using that path we find the argument vector
  ; For def, we modify the stuff we did for docstrings and use that.
  ; But that isn't yet implemented.
  (cond
    (= (zprint.zutil/string (zprint.zutil/down* zloc-root)) "defn")
      (loop [zloc (zprint.zutil/down* zloc-root)]
        (when zloc
          (let [zloc-tag (zprint.zutil/tag zloc)]
            (if (= zloc-tag :vector)
              zloc
              (if (= zloc-tag :list)
                (find-args-from-focus zloc-focus)
                (recur (zprint.zutil/zrightnws zloc)))))))
    :else nil))

(defn get-frame-map
  "Given zippers for a function and the call site inside of the function,
  see if we can find the formal arguments (i.e., params) for the function 
  and return everything in a convenient map."
  [options zloc zloc-root zcall]
  (let [param-zloc (find-args zloc zloc-root)
        #_(println "get-param-map: param-zloc:" (z/string param-zloc))
        [_ param-path] (when param-zloc
                         (zprint.zutil/find-root-and-path-nw param-zloc))
        args (:args zcall)
        thread-bindings (:thread-bindings zcall)
        env-map (:env-map zcall)
        params (map read-string
                 (zprint.zutil/zmap zprint.zutil/string param-zloc))
        clean-args (if (:arg-clean-fn options)
                     (map (:arg-clean-fn options) params args))]
    {:args args,
     :params params,
     :clean-args clean-args,
     :zloc zloc,
     :param-path param-path,
     :thread-bindings thread-bindings,
     :env-map env-map}))
