(ns zpst.core-test
  (:require [expectations :refer :all]
            [zpst.core :refer :all]
            [clojure.repl :refer :all]))

;;
;; NOTE: (with-out-str <fn>) is used to capture and
;; ignore printed output from a number of functions.
;;

(expect 1 (find-in-seq [:a :b :c] :b))
(expect nil (find-in-seq [:a :b :c] :d))

;;
;; # Functions which will have errors in some cases
;;

(defn test-bug
  "If called with c = 8, it will throw an exception."
  [a b c]
  (let [a 5 b (if (= c 8) "hi" 4)] (+ a b)))

(defn test-deep
  "A function on the way down."
  [[arg0 arg1 arg2]]
  (when (and arg0 arg1 arg2) (test-bug arg0 arg1 arg2)))

(defn test-fn
  "A multithreaded function to use for testing zpst."
  [stuff]
  (doall (pmap test-deep (partition 3 1 (range 20)))))

;;
;; # Basics
;;

(expect :exception (try (test-fn :bother) (catch Exception e :exception)))

(expect :exception
        (try (get-arg-number 3 '[a b c] 9) (catch Exception e :exception)))

;;
;; # Generate an exception
;;

(try (test-fn :bother) (catch Exception e (def ex1 e) nil))

;;
;; # Hook functions
;;

(with-out-str (collect ex1))

;;
;; # Repeat the exception
;;

(try (test-fn :bother) (catch Exception e (def ex2 e) nil))

;;
;; # Analyze the results
;;

(with-out-str (analyze ex2))

;;
;; # Now, we can work with the results
;;

(expect 7 (do (with-out-str (set-frame! 0)) (get-arg 1)))
(expect 7 (do (with-out-str (set-frame! 0)) (get-arg 'b)))
(expect 7 (do (with-out-str (set-frame! 0)) (get-arg "b")))

(expect '(6 7 8) (do (with-out-str (set-frame! 1)) (get-arg 0)))

;;
;; This should work, because the third argument isn't 8
;;

(expect 9
        (do (with-out-str (set-frame! 1))
            (set-arg! 0 '(1 2 3))
            (re-eval)
            (get-re-eval-return)))
