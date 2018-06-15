# epst

Do what `pst` does, but do it completely for all of the nested
exceptions.  `pst` is a great command, but at least in early
Clojure 1.9.0, `pst` has some interesting quirks if you are using
it on exceptions that are nested (due to multithreaded operations,
for instance).  In order to build `zpst`, I needed a tool that would
show me all of the exceptions without trying to make them convienient
for day to day debugging, so I wrote `epst`.  You probably won't need
it, but if you want to see all that there is to see in an exception,
it can be useful.

```clojure
(epst)
(epst <exception>)
(epst <options>)
(epst <exception> <options>)
```
An example:

```clojure
zpst.core=> (zprint.core/czprint-fn zprint.zprint/fzprint-reader-macro {:dbg-bug? true})

ClassCastException java.lang.String cannot be cast to java.lang.Number  clojure.lang.Numbers.add (Numbers.java:128)
zpst.core=> (epst)
0  Exception:	java.util.concurrent.ExecutionException
0  Message:	java.util.concurrent.ExecutionException: java.util.concurrent.ExecutionException: java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Number
	java.util.concurrent.FutureTask.report (FutureTask.java:122)
	java.util.concurrent.FutureTask.get (FutureTask.java:192)
	clojure.core/deref-future (core.clj:2292)
	clojure.core/future-call/reify--8097 (core.clj:6894)
	clojure.core/deref (core.clj:2312)
	clojure.core/deref (core.clj:2298)
	zprint.zprint/zat (zprint.cljc:90)
	zprint.zprint/zat (zprint.cljc:85)
	zprint.zprint/fzprint-hang-remaining (zprint.cljc:2405)
	zprint.zprint/fzprint-hang-remaining (zprint.cljc:2227)
	zprint.zprint/fzprint-hang-remaining (zprint.cljc:2431)
	zprint.zprint/fzprint-hang-remaining (zprint.cljc:2227)
	zprint.zprint/fzprint-list* (zprint.cljc:2792)
	zprint.zprint/fzprint-list* (zprint.cljc:2495)
	zprint.zprint/fzprint-list (zprint.cljc:2864)
	zprint.zprint/fzprint-list (zprint.cljc:2861)
	zprint.zprint/fzprint* (zprint.cljc:3663)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint (zprint.cljc:3908)
	zprint.zprint/fzprint (zprint.cljc:3896)
	clojure.core/partial/fn--5565 (core.clj:2629)
	zprint.zutil/zredef-call/fn--4965 (zutil.cljc:640)
	clojure.core/with-redefs-fn (core.clj:7434)
	clojure.core/with-redefs-fn (core.clj:7418)
	zprint.zutil/zredef-call (zutil.cljc:581)
	zprint.zutil/zredef-call (zutil.cljc:577)
	zprint.core/fzprint-style (core.cljc:201)
	zprint.core/fzprint-style (core.cljc:147)
	zprint.core/zprint* (core.cljc:318)
	zprint.core/zprint* (core.cljc:301)
	zprint.core/czprint-str-internal (core.cljc:382)
	zprint.core/czprint-str-internal (core.cljc:362)
	zpst.core/eval15237 (form-init2978558143991234599.clj:1)
	zpst.core/eval15237 (form-init2978558143991234599.clj:1)
	clojure.lang.Compiler.eval (Compiler.java:7062)
	clojure.lang.Compiler.eval (Compiler.java:7025)
	clojure.core/eval (core.clj:3206)
	clojure.core/eval (core.clj:3202)
	clojure.main/repl/read-eval-print--8572/fn--8575 (main.clj:243)
	clojure.main/repl/read-eval-print--8572 (main.clj:243)
	clojure.main/repl/fn--8581 (main.clj:261)
	clojure.main/repl (main.clj:261)
	clojure.main/repl (main.clj:177)
	clojure.tools.nrepl.middleware.interruptible-eval/evaluate/fn--820 (interruptible_eval.clj:87)
	clojure.core/with-bindings* (core.clj:1965)
	clojure.core/with-bindings* (core.clj:1965)
	clojure.tools.nrepl.middleware.interruptible-eval/evaluate (interruptible_eval.clj:85)
	clojure.tools.nrepl.middleware.interruptible-eval/evaluate (interruptible_eval.clj:55)
	clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval/fn--865/fn--868 (interruptible_eval.clj:222)
	clojure.tools.nrepl.middleware.interruptible-eval/run-next/fn--860 (interruptible_eval.clj:190)
	java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1142)
	java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617)
	java.lang.Thread.run (Thread.java:745)
Caused by:
1  Exception:	java.util.concurrent.ExecutionException
1  Message:	java.util.concurrent.ExecutionException: java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Number
	java.util.concurrent.FutureTask.report (FutureTask.java:122)
	java.util.concurrent.FutureTask.get (FutureTask.java:192)
	clojure.core/deref-future (core.clj:2292)
	clojure.core/future-call/reify--8097 (core.clj:6894)
	clojure.core/deref (core.clj:2312)
	clojure.core/pmap/step--8110/fn--8114 (core.clj:6945)
	clojure.lang.LazySeq.sval (LazySeq.java:40)
	clojure.lang.LazySeq.seq (LazySeq.java:49)
	clojure.lang.RT.seq (RT.java:528)
	clojure.core/seq--5124 (core.clj:137)
	clojure.core/filter/fn--5614 (core.clj:2801)
	clojure.lang.LazySeq.sval (LazySeq.java:40)
	clojure.lang.LazySeq.seq (LazySeq.java:56)
	clojure.lang.RT.seq (RT.java:528)
	clojure.lang.RT.countFrom (RT.java:643)
	clojure.lang.RT.count (RT.java:636)
	zprint.zprint/contains-nil? (zprint.cljc:275)
	zprint.zprint/contains-nil? (zprint.cljc:271)
	zprint.zprint/fzprint-map-two-up (zprint.cljc:1060)
	zprint.zprint/fzprint-map-two-up (zprint.cljc:999)
	zprint.zprint/fzprint-binding-vec (zprint.cljc:1414)
	zprint.zprint/fzprint-binding-vec (zprint.cljc:1397)
	zprint.zprint/fzprint-hang-unless-fail (zprint.cljc:652)
	zprint.zprint/fzprint-hang-unless-fail (zprint.cljc:645)
	zprint.zprint/fzprint-list* (zprint.cljc:2599)
	zprint.zprint/fzprint-list* (zprint.cljc:2495)
	zprint.zprint/fzprint-list (zprint.cljc:2864)
	zprint.zprint/fzprint-list (zprint.cljc:2861)
	zprint.zprint/fzprint* (zprint.cljc:3663)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint-seq (zprint.cljc:1585)
	zprint.zprint/fzprint-seq (zprint.cljc:1560)
	zprint.zprint/fzprint-hang-remaining/fn--6088 (zprint.cljc:2269)
	clojure.core/binding-conveyor-fn/fn--5476 (core.clj:2022)
	java.util.concurrent.FutureTask.run (FutureTask.java:266)
	java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1142)
	java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617)
	java.lang.Thread.run (Thread.java:745)
Caused by:
2  Exception:	java.util.concurrent.ExecutionException
2  Message:	java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Number
	java.util.concurrent.FutureTask.report (FutureTask.java:122)
	java.util.concurrent.FutureTask.get (FutureTask.java:192)
	clojure.core/deref-future (core.clj:2292)
	clojure.core/future-call/reify--8097 (core.clj:6894)
	clojure.core/deref (core.clj:2312)
	clojure.core/deref (core.clj:2298)
	clojure.core/map/fn--5587 (core.clj:2745)
	clojure.lang.LazySeq.sval (LazySeq.java:40)
	clojure.lang.LazySeq.seq (LazySeq.java:56)
	clojure.lang.RT.seq (RT.java:528)
	clojure.core/seq--5124 (core.clj:137)
	clojure.core/filter/fn--5614 (core.clj:2801)
	clojure.lang.LazySeq.sval (LazySeq.java:40)
	clojure.lang.LazySeq.seq (LazySeq.java:49)
	clojure.lang.RT.seq (RT.java:528)
	clojure.lang.RT.countFrom (RT.java:643)
	clojure.lang.RT.count (RT.java:636)
	zprint.zprint/contains-nil? (zprint.cljc:275)
	zprint.zprint/contains-nil? (zprint.cljc:271)
	zprint.zprint/fzprint-map-two-up (zprint.cljc:1060)
	zprint.zprint/fzprint-map-two-up (zprint.cljc:999)
	zprint.zprint/fzprint-pairs (zprint.cljc:1468)
	zprint.zprint/fzprint-pairs (zprint.cljc:1458)
	zprint.zprint/fzprint-hang (zprint.cljc:1433)
	zprint.zprint/fzprint-hang (zprint.cljc:1422)
	zprint.zprint/fzprint-list* (zprint.cljc:2625)
	zprint.zprint/fzprint-list* (zprint.cljc:2495)
	zprint.zprint/fzprint-list (zprint.cljc:2864)
	zprint.zprint/fzprint-list (zprint.cljc:2861)
	zprint.zprint/fzprint* (zprint.cljc:3663)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint-two-up (zprint.cljc:854)
	zprint.zprint/fzprint-two-up (zprint.cljc:675)
	clojure.core/partial/fn--5567 (core.clj:2635)
	clojure.core/pmap/fn--8105/fn--8106 (core.clj:6942)
	clojure.core/binding-conveyor-fn/fn--5476 (core.clj:2022)
	java.util.concurrent.FutureTask.run (FutureTask.java:266)
	java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1142)
	java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617)
	java.lang.Thread.run (Thread.java:745)
Caused by:
3  Exception:	java.lang.ClassCastException
3  Message:	java.lang.String cannot be cast to java.lang.Number
	clojure.lang.Numbers.add (Numbers.java:128)
	zprint.zprint/fzprint-reader-macro (zprint.cljc:3533)
	zprint.zprint/fzprint-reader-macro (zprint.cljc:3507)
	zprint.zprint/fzprint* (zprint.cljc:3690)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint-one-line (zprint.cljc:1547)
	zprint.zprint/fzprint-one-line (zprint.cljc:1518)
	zprint.zprint/fzprint-list* (zprint.cljc:2582)
	zprint.zprint/fzprint-list* (zprint.cljc:2495)
	zprint.zprint/fzprint-list (zprint.cljc:2864)
	zprint.zprint/fzprint-list (zprint.cljc:2861)
	zprint.zprint/fzprint* (zprint.cljc:3663)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint-one-line (zprint.cljc:1547)
	zprint.zprint/fzprint-one-line (zprint.cljc:1518)
	zprint.zprint/fzprint-list* (zprint.cljc:2582)
	zprint.zprint/fzprint-list* (zprint.cljc:2495)
	zprint.zprint/fzprint-list (zprint.cljc:2864)
	zprint.zprint/fzprint-list (zprint.cljc:2861)
	zprint.zprint/fzprint* (zprint.cljc:3663)
	zprint.zprint/fzprint* (zprint.cljc:3619)
	zprint.zprint/fzprint-two-up (zprint.cljc:854)
	zprint.zprint/fzprint-two-up (zprint.cljc:675)
	clojure.core/partial/fn--5567 (core.clj:2635)
	clojure.core/pmap/fn--8105/fn--8106 (core.clj:6942)
	clojure.core/binding-conveyor-fn/fn--5476 (core.clj:2022)
	java.util.concurrent.FutureTask.run (FutureTask.java:266)
	java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1142)
	java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617)
	java.lang.Thread.run (Thread.java:745)
nil
```

## Configuring epst

There isn't much to configure with epst, however one of its features
is to remove stack frames which match any of a set of strings.  This is
configured by:

#### :elide? <text style="color:#A4A4A4;"><small>true</small></text>

If true, epst will ignore any stack frame which contains a string
in the set `:elide-lines-with`.

#### :elide-lines-with <text style="color:#A4A4A4;"><small>#{"apply" "hook" "doInvoke"}</small></text>

This is a set of strings, and by default epst will not show any stack
frame which contains one of these strings.  This will allow examination
of the exceptions that result from recreating a problem after running
`(collect)`, which hooks a lot of functions and massively expands the
stack backtrace.

