(ns zpst.spec
  (:require [clojure.spec.alpha :as s]))

;;
;; # Macros to help with using Clojure Spec
;;

(defmacro only-keys
  "Like keys, but checks that only the keys mentioned are allowed. 
  Credit to Alistair Roche, 9/25/16 post in Clojure google group."
  [& {:keys [req req-un opt opt-un], :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      (constantly true))))


;;
;; # Compatibility
;;
;; Try to avoid loading any namespaces we don't need all the
;; time.  These can go away when we get to just 1.9
;;

(defn zany? [x] true)
(defn zboolean? [x] (instance? Boolean x))

;!zprint {:list {:constant-pair-min 2}}

;;
;; # Specs for the options map
;;

;;
;; # Fundamental values
;;

(s/def ::boolean (s/nilable zboolean?))
(s/def ::nilable-number (s/nilable number?))

;;
;; # Show
;;
;; The show-keywords have to be the same as the show
;; leaf map keys without the ? at the end.
;;

(s/def ::show-keyword #{:docstring :args :params :call :name :stack :frame})

;;
;; ## Show leaf map keys
;;

(s/def ::docstring? ::boolean)
(s/def ::call? ::boolean)
(s/def ::args? ::boolean)
(s/def ::color? ::boolean)
(s/def ::params? ::boolean)
(s/def ::name? ::boolean)
(s/def ::stack? ::boolean)
(s/def ::frame? ::boolean)

;;
;; # Leaf map keys
;;

(s/def ::fns? ::boolean)
(s/def ::ns (s/coll-of symbol? :kind sequential?))
(s/def ::zdb? ::boolean)
(s/def ::show (s/coll-of ::show-keyword :kind set?))
(s/def ::arg-clean-fn var?)
(s/def ::arg-vec-options-fn var?)
(s/def ::depth number?)
(s/def ::fns? ::boolean)
(s/def ::zdb? ::boolean)
; Make sure every place these two specs are used, that the resulting key
; sequence appears in zprint-option-ks in config.clj.
(s/def ::zprint-args map?)
(s/def ::zprint-source map?)
(s/def ::elide-lines-with (s/nilable (s/coll-of string? :kind set?)))
(s/def ::elide? ::boolean)
(s/def ::dbg? ::boolean)
(s/def ::configured? ::boolean)
(s/def ::hook-fns (s/coll-of var? :kind sequential?))

;;
;; # Elements of the top level options map
;;

(s/def ::collect (only-keys :opt-un [::fns? ::ns ::hook-fns]))
(s/def ::analyze (only-keys :opt-un [::zdb?]))
(s/def ::commands
  (only-keys :opt-un [::show ::arg-clean-fn ::arg-vec-options-fn ::zprint-args
                      ::zprint-source ::docstring? ::call? ::args? ::color?
                      ::params? ::name? ::stack?]))
(s/def ::zpst
  (only-keys :opt-un [::show ::depth ::dbg? ::fns? ::zdb? ::arg-clean-fn
                      ::arg-vec-options-fn ::zprint-args ::zprint-source
                      ::docstring? ::call? ::color? ::args? ::params? ::name?
                      ::stack?]))
(s/def ::epst (only-keys :opt-un [::elide-lines-with ::elide? ::dbg?]))
(s/def ::remove (only-keys :opt-un [::commands]))

;;
;; # Top level options map
;;

(s/def ::options
  (only-keys :opt-un [::collect ::analyze ::commands ::zpst ::epst ::remove
                      ::configured?]))

(defn numbers-or-number-pred?
  "If they are both numbers and are equal, or the first is a number 
  and the second one is a pred."
  [x y]
  (and (number? x) (or (= x y) (= y :clojure.spec.alpha/pred))))

(defn problem-ks
  "Return the key sequence for this problem.  This is totally empiric, and
  not based on any real understanding of what explain-data is returning as
  the problem.  It seems to stick integers into the :in for no obvious reason.
  This version has three heuristics, described in the comments in the code."
  [problem]
  (let [path (:path problem)
        last-path (last path)
        last-num (and (number? last-path) last-path)
        ks (:in problem)
        #_(println ":in" ks)
        #_(println ":path" path)
        ; First heuristic: trim ks to be no longer than path
        ks (into [] (take (count path) ks))
        ; Second heuristic: If the last thing in ks is a number and
        ; the last thing in the path is a pred, then trim the number
        last-ks (last ks)
        #_(println "ks na:" ks)
        ks (if (and (number? last-ks) (= last-path :clojure.spec.alpha/pred))
             (into [] (butlast ks))
             ks)
        ; Third heuristic: Remove the first number in ks that is at
        ; the same index as a matching number in the path, if it is not
        ; equal to the val.
        ks-equal (map #(when (numbers-or-number-pred? %1 %2) %1) ks path)
        matching-index (reduce
                         #(if (number? %2) (reduced %1) (inc %1) #_(dec %1))
                         0 ks-equal)
        matching-index (when (< matching-index (count ks)) matching-index)
        #_(println "ks mi:" ks "matching-index:" matching-index)
        ks (if (and matching-index
                    (not= (nth ks matching-index) (:val problem)))
             (let [[begin end] (split-at matching-index ks)]
               (into [] (concat begin (drop 1 end))))
             ks)]
    ks))

(defn ks-phrase
  "Take a key-sequence and a value, and decide if we want to 
  call it a value or a key."
  [problem]
  (let [val (:val problem)
        ks (problem-ks problem)]
    (if ((set ks) val)
      (str "In the key-sequence " ks " the key " (pr-str val))
      (str "The value of the key-sequence " ks " -> " (pr-str val)))))

(defn map-pred
  "Turn some predicates into something more understandable."
  [pred]
  (case pred
    "zboolean?" "boolean"
    "zprint.spec/zboolean?" "boolean"
    "clojure.core/set?" "set"
    "clojure.core/sequential?" "sequential"
    "clojure.core/number?" "number"
    "clojure.core/map?" "map"
    "map?" "map"
    "string?" "string"
    pred))

(defn map-pred-alt
  "Turn some predicates into something more understandable."
  [pred]
  (case pred
    'zboolean? 'boolean?
    pred))

(defn explain-more
  "Try to do a better job of explaining spec problems."
  [explain-data-return]
  (when explain-data-return
    (let [problem-list (:clojure.spec.alpha/problems explain-data-return)
          problem-list (remove #(= "nil?" (str (:pred %))) problem-list)
          val-map (group-by :val problem-list)
          key-via-len-seq
            (map (fn [[k v]] [k (apply min (map (comp count :via) v))]) val-map)
          [key-choice min-via] (first (sort-by second key-via-len-seq))
          problem (first (filter (comp (partial = min-via) count :via)
                           (val-map key-choice)))]
      (cond (clojure.string/ends-with? (str (:pred problem)) "?")
              (str (ks-phrase problem)
                   " was not a " (map-pred (str (:pred problem))))
            (set? (:pred problem)) (str (ks-phrase problem)
                                        " was not recognized as valid!")
            :else (str "what?")))))

(defn validate-basic
  "Using spec defined above, validate the given options map.  Return
  nil if no errors, or a string containing errors if any."
  ([options source-str]
   #_(println "Options:" options)
   (try (if (s/valid? ::options options)
          nil
          (if source-str
            (str "In " source-str
                 ", " (explain-more (s/explain-data ::options options)))
            (explain-more (s/explain-data ::options options))))
        (catch Exception e
          #_(println "Exception:" (str e))
          #_(println "type of exception:" (type e))
          #_(println ":cause" (:cause e))
          (if source-str
            (str "In "
                 source-str
                 ", validation failed completely because: "
                 (str e)
                 #_(.-message e))
            (str "Validation failed completely because: "
                 (str e)
                 #_(.-message e))))))
  ([options] (validate-basic options nil)))

#_(defn explain
    "Take an options map and explain the result of the spec.  This is
  really here for testing purposes."
    ([options show-problems?]
     (let [problems (s/explain-data ::options options)]
       (when show-problems? (zprint.core/czprint problems))
       (explain-more problems)))
    ([options] (explain options nil)))
