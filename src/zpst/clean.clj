(ns zpst.clean
  (:require clojure.string
            clojure.set
            [zprint.zutil]
            [zprint.core]))

;;
;; # Clean Args
;;

(defn beginning-str
  "Return the first n characters of the string."
  [n s]
  (subs s 0 (min (count s) n)))

(defn clean-value
  "Prototype of a function that cleans up a value for printing. Returns
  zprint options and/or a cleaned."
  [local value]
  (cond (zprint.core/zipper? value)
          (str "zipper: " (beginning-str 70 (zprint.zutil/string value)))
        (= local 'options) (str "<options-map>")
        (= (:as local) 'options) (str "<options-map>")
        :else value))

(defn zprint-options
  "Input is a binding vector with params and actual arguments.  Output
  is a map of zprint options to use to print the binding vector, or
  nil for no change to existing options."
  [binding-vec]
  {:max-length [20 5 2 0]})

(defn clean-value-alt
  "Prototype of a function that cleans up a value for printing."
  [local value]
  (cond (zprint.core/zipper? value)
          (str "zipper: " (beginning-str 70 (zprint.zutil/string value)))
        (= local 'options) (str "<options-map>")
        (= (:as local) 'options) (str "<options-map>")
        :else value))

;;
;; # Clean Stack Functions
;;

(declare seq-starts-with?)

(defn seq-compare
  "Take two elements of a seq, and if they compare successfully, return
  true. If the pattern is not a collection, they must be = unless the
  pattern is a string containing *, in which case it always matches.  If
  the pattern is a collection, then the element is assumed to be a string,
  and it is split by $, and then if it starts with the collection which is
  the pattern it matches."
  [pattern element]
  (cond (coll? pattern) (let [split (clojure.string/split element #"\$")]
                          (seq-starts-with? pattern split))
        (= pattern "*") true
        :else (= pattern element)))

(defn seq-starts-with?
  "Take two seqs, and if the first one is the beginning of the
  second one, return true. See seq-compare for details of the comparison."
  [seq1 seq2]
  (every? identity (map #(seq-compare %1 %2) seq1 seq2)))

(defn seq-starts-with-any?
  "Take two seqs.  The first is a seq of patterns, and the second one just 
  a seq.  If any of the patterns in the first seq match the second seq, then
  return the length of the pattern that matched.  See seq-compare for detail
  of how seqs are compared."
  [pattern-seq seq-to-match]
  (loop [patterns pattern-seq]
    (when patterns
      (if (seq-starts-with? (first patterns) seq-to-match)
        (count (first patterns))
        (recur (next patterns))))))

(defn filter-pattern-seq
  "Take two seqs, one a seq of patterns, and one a seq to match.  Remove all
  of the elements of any of the patterns that appear in the seq to match. See
  seq-starts-with? for how patterns are used."
  [pattern-seq f seq]
  (loop [in seq
         match (map f in)
         out []]
    (if in
      (if-let [remove-len (seq-starts-with-any? pattern-seq match)]
        (recur (drop remove-len in) (drop remove-len match) out)
        (recur (next in) (next match) (conj out (first in))))
      out)))

(defn clean-hooks
  "Remove hook functions from stack."
  [st]
  (filter-pattern-seq
    [[["clojure.core" "apply"] ["clojure.core" "apply"] ["*" "hook_fn"]
      ["*" "hook_fn"] "*" "*" ["clojure.core" "apply"] ["clojure.core" "apply"]
      ["robert.hooke"] ["clojure.core" "apply"] ["clojure.core" "apply"]
      ["robert.hooke"] ["robert.hooke"] ["robert.hooke"]]
     [["clojure.core" "apply"] ["clojure.core" "apply"] ["*" "hook_fn"]
      ["*" "hook_fn"] "*" ["clojure.core" "apply"] ["clojure.core" "apply"]
      ["robert.hooke"] ["clojure.core" "apply"] ["clojure.core" "apply"]
      ["robert.hooke"] ["robert.hooke"] ["robert.hooke"]]]
    (comp str (memfn ^java.lang.StackTraceElement getClassName))
    st))
