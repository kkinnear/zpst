(ns zpst.hook
  (:require clojure.string
            clojure.set
            [zpst.config :refer [get-options]]
            [zpst.zpst :refer [zpst*]]
            [robert.hooke :as h :only [add-hook]]))

;;
;; # Declarations
;;

(defonce hooked-fns (atom []))

;;
;; # Exception Definition
;;

(defn ex-info-no-print-data
  "We store a *lot* of information in the data of an ex-info object,
  and when a java.util.concurrent.ExecutionException tries to create
  its message, it does a (.toString <our-ex-info-exception>), which
  generates not only a huge string, but also sometimes runs into
  problems.  There is no value to this, so we will create a subclass
  of clojure.lang.ExceptionInfo (which is what ex-info creates)
  which overrides the (.toString ...) method, and does not include
  the data in the string."
  [msg map cause]
  (proxy [clojure.lang.ExceptionInfo] [msg map cause]
    (toString [] (.getMessage this))))

;;
;; # Environment
;;

(defn build-var-map
  "Given the return from (ns-publics <ns>), which is a map of 
  public intern mappings for the namespace -- which is actually a map
  of aliases to Vars, build a map of all of the
  Vars and their current values."
  [alias-map]
  (let [var-seq (vals alias-map)] (zipmap var-seq (map var-get var-seq))))

(defn add-ns-env
  "Given a existing map and a namespace, use build-var-map to
  build the environment for that namespace and merge it in."
  [existing-map ns-name]
  (merge existing-map (build-var-map (ns-publics ns-name))))

(defn env-map
  "Given a vector of namespace names, do build-var-map for each of
  them and merge the results into an overall map."
  [ns-vec]
  (reduce add-ns-env {} ns-vec))

;;
;; # Function Hooking Support
;;

(defn hook-fn
  "Take a function and argument list, and do the function, and catch
  any exceptions and pass them up the line. This could be firmed
  up with namespace qualified keywords!  Lots of code in here to
  catch any exceptions that might happen while we are processing
  exceptions."
  [fn & args]
  (try
    (apply fn args)
    (catch Exception e
      (let [exdata (ex-data e)
            zcalls
              (try (conj (or (:zcalls exdata) [])
                         {:fn fn,
                          :args args,
                          :thread-bindings (get-thread-bindings),
                          :env-map (env-map (:ns (:collect (get-options))))})
                   (catch Exception f (println "hook-fn 1:" (pr-str (type f)))))
            data (or (:zcalls exdata) [])
            #_(try (do (println "\nfn:" (.toString fn)
                                "\n\ttype:" (type e)
                                "\n\thash-code:" (.hashCode e)
                                "\n\tcause-type:" (type (.getCause e))
                                "\n\tcause-hash:" (when (.getCause e)
                                                    (.hashCode (.getCause e)))
                                "\n\tPrevious fn:" (when (:fn (first data))
                                                     (.toString
                                                       (:fn (last data))))))
                   (catch Exception g (println "hook-fn 2:" (pr-str (type g)))))
            new-exception
              (try (ex-info-no-print-data (str "Exception in fn: " fn)
                                          {:zcalls zcalls}
                                          (if exdata (.getCause e) e))
                   (catch Exception h (println "hook-fn 3:" (pr-str (type h)))))
            #_(println "\tAbout to throw:"
                       (pr-str (type new-exception))
                       (.hashCode new-exception))]
        (throw new-exception)))))

;;
;; # Function Hooking API
;;

(defn unhook
  "Unhook all currently hooked functions."
  []
  (doseq [fv @hooked-fns]
    (robert.hooke/remove-hook fv :zpst)
    #_(println "removed hook for:" fv))
  (println "Unhooked" (count @hooked-fns) "functions")
  (reset! hooked-fns []))

(defn hook
  "Given an exception, hook all of the functions that it calls where
  we can find the source."
  ([options e]
   #_(unhook)
   (let [fns (or (:fns options) (zpst* e (:collect (get-options))))
         fns (distinct fns)
         fns (remove #(or (= "clojure.core/apply" %) (= "clojure.core/deref" %))
               fns)
         fn-vars (map (comp resolve symbol) fns)]
     #_(println "fn-vars:" fn-vars)
     (doseq [fv fn-vars]
       (robert.hooke/add-hook fv :zpst #'hook-fn)
       #_(println "added hook for:" fv))
     (println "Hooked" (count fn-vars) "functions")
     (reset! hooked-fns fn-vars)
     nil))
  ([] (hook *e nil)))
