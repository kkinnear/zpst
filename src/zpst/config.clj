(ns zpst.config
  (:require clojure.string
            clojure.set
            [zpst.clean :refer [clean-value zprint-options]]
            [zpst.spec :refer [validate-basic]]
            [zprint.zprint :refer [remove-key-seq]]
            [zprint.config :refer
             [validate-options diff-deep-ks merge-deep perform-remove
              remove-keys get-config-from-file key-seq apply-style] :rename
             {validate-options zprint-validate-options}]))

;;
;; # Program Version
;;

(defn about "Return version of this program." [] (str "zpst-0.1.1"))

;;
;; # External Configuration
;;
;; Will read this when run standalone, or first time a zpst
;; function is used when used as a library.
;;

(def zpstrc ".zpstrc")

;;
;; # Declarations
;;

(def default-zpst-options
  {:analyze {:zdb? true},
   :collect {:fns? true, :hook-fns nil, :ns '[zprint.zfns]},
   :commands {:arg-clean-fn nil, ; #'clean-value,
              :arg-vec-options-fn #'zprint-options,
              :args? true,
              :call? true,
              :color? true,
              :docstring? false,
              :frame? false,
              :name? true,
              :params? true,
              :show #{},
              :stack? true,
              :zprint-args {:max-length [20 5 2 0]},
              :zprint-source {:output {:elide "[...]",
                                       :focus {:surround [8 2]},
                                       :lines [0]}}},
   :configured? false,
   :epst {:elide-lines-with #{"apply" "hook" "doInvoke"},
          :elide? true,
          :dbg? false},
   :zpst {:arg-clean-fn nil, ; #'clean-value,
          :arg-vec-options-fn #'zprint-options,
          :args? true,
          :call? true,
          :color? true,
          :depth 20,
          :docstring? false,
          :fns? false,
          :frame? true,
          :name? true,
          :params? true,
          :show #{},
          :stack? true,
          :zdb? false,
          :zprint-args {:max-length [20 5 2 0]},
          :zprint-source
            {:output {:elide "[...]", :focus {:surround [8 2]}, :lines [0]}}}})

(def explain-hide-keys
  [:configured? [:analyze :zdb?] [:analyze :fns?] [:collect :fns?]
   [:collect :zdb?] [:zpst :fns? :show] [:zpst :zdb?] [:commands :show]])

;;
;; # Mutable Options storage
;;

(def configured-options (atom default-zpst-options))

(def explained-options (atom default-zpst-options))

(def explained-sequence (atom 1))

(defn inc-explained-sequence
  "Return current explained-seqence and add one to it."
  []
  (swap! explained-sequence inc))

(defn get-options
  "Return the currently configured options."
  []
  @configured-options)

(defn get-default-options
  "Return the base default options."
  []
  default-zpst-options)

(defn reset-options!
  "Replace options to be used on every call.  You must have validated
  these options already!"
  ([updated-map doc-map]
   (reset! configured-options updated-map)
   (when doc-map (reset! explained-options doc-map)))
  ([updated-map] (reset-options! updated-map nil)))

(defn reset-default-options!
  "Remove any previously set options."
  []
  (reset! configured-options default-zpst-options)
  (reset! explained-options default-zpst-options))

;;
;; ## Explained options, also known as the doc-map
;;

(defn set-explained-options!
  "Set options to be used on every call."
  [doc-map]
  (reset! explained-options doc-map))

(defn get-explained-options
  "Return any previously set doc-map."
  []
  (assoc (remove-keys @explained-options explain-hide-keys) :version (about)))

(defn get-explained-all-options
  "Return any previously set doc-map complete."
  []
  (assoc @explained-options :version (about)))

(defn get-default-explained-all-options
  "Return the base explained options, matches get-default-options"
  []
  default-zpst-options)

(declare config-and-validate)
(declare config-and-validate-external)
(declare config-set-options!)

;;
;; # Configure Everything
;;

(defn internal-set-options!
  "Validate the new options, and update both the saved options
  and the doc-map as well.  Will throw an exception for errors."
  [doc-string doc-map existing-options new-options]
  (let [[updated-map new-doc-map error-vec]
          (config-and-validate doc-string doc-map existing-options new-options)]
    (if error-vec
      (throw (Exception. (apply str
                           "set-options! for " doc-string
                           " found these errors: " error-vec)))
      (do (reset-options! updated-map new-doc-map) nil))))

(defn config-configure-all!
  "Do external configuration regardless of whether or not it has
  already been done, replacing any internal configuration.  Returns
  nil if successful, a vector of errors if not.  Argument, if it
  exists, says whether or not to try to load additional libraries.
  Defaults to true, unusually enough."
  []
  ; Any config changes prior to this will be lost, as
  ; config-and-validate-external works from the default options!
  (let [[zpst-options doc-map errors] (config-and-validate-external)]
    (if errors
      errors
      (do (reset-options! zpst-options doc-map)
          (config-set-options! {:configured? true} "internal")
          nil))))

(defn config-set-options!
  "Add some options to the current options, checking to make
  sure that they are correct.  External interface for setting
  options (i.e., called by zpst.core/set-options!)."
  ([new-options doc-str]
   ; avoid infinite recursion, while still getting the doc-map updated
   (when (and (not (:configured? (get-options)))
              (not (:configured? new-options)))
     (config-configure-all!))
   (internal-set-options! doc-str
                          (get-explained-all-options)
                          (get-options)
                          new-options))
  ([new-options]
   (config-set-options! new-options
                        (str "repl or api call " (inc-explained-sequence)))))

;;
;; # Validate zpst options map
;;

(defn empty-to-nil
  "If the sequence is empty, then return nil, else return the sequence."
  [empty-seq]
  (when-not (empty? empty-seq) empty-seq))

(declare validate-style-map)

(def zprint-option-ks
  [[:zpst :zprint-args] [:zpst :zprint-source] [:commands :zprint-args]
   [:commands :zprint-source]])

(defn validate-options
  "Validate an options map, which contains embedded zprint options
  maps.  Requires source-str, a descriptive phrase which will be
  included in any errors.  Returns nil for success, a string with
  error(s) if not."
  ([options source-str]
   #_(println "validate-options:" options)
   (when options
     (let [zprint-options (map (partial get-in options) zprint-option-ks)
           error-seq
             (mapv zprint-validate-options
               zprint-options
               (mapv (partial str (str "the zprint options map located at "))
                 zprint-option-ks))
           error-seq (remove nil? error-seq)
           error-seq (map (partial str (str "In " source-str ", ")) error-seq)
           error-str (apply str (interpose ", " error-seq))
           error-str (if (empty? error-str) nil error-str)]
       #_(println "options-wo-zprint" options-wo-zprint)
       (empty-to-nil
         (apply str
           (interpose ", "
             (remove #(or (nil? %) (empty? %))
               (conj []
                     (validate-basic (dissoc options :style-map) source-str)
                     error-str))))))))
  ([options] (validate-options options nil)))

;;
;; # Configure one map
;;

(defn config-and-validate
  "Do a single new map. Returns [updated-map new-doc-map error-vec].
  Handles removing elements from existing map as well as validation.
  Depends on existing-map to be the full, current options map!"
  [doc-string doc-map existing-map new-map]
  #_(println "config-and-validate:" new-map)
  (if new-map
    (let [errors (validate-options new-map doc-string)
          ; remove set elements, and then remove the :remove key too
          [existing-map new-map new-doc-map]
            (perform-remove doc-string doc-map existing-map new-map)
          new-updated-map (merge-deep existing-map new-map)
          new-doc-map (diff-deep-ks doc-string
                                    new-doc-map
                                    (key-seq new-map)
                                    new-updated-map)]
      [new-updated-map new-doc-map errors])
    ; if we didn't get a map, just return something with no changes
    [existing-map doc-map nil]))

;;
;; This needs to be fixed up to integrate with config-and-validate
;; somehow.
;;

(defn validate-incoming-options
  "Take some incoming options from a call and validate them.
  Should be called whether or not we really have any options, since
  this ensures that the ~/.zpst.rc file is used to initialize everything.
  Either throws an exception or returns the options."
  [doc-string incoming-options]
  (let [; Do what config-and-validate does, minus the doc-map
        configure-errors (when-not (:configured? (get-options))
                           (config-configure-all!))
        errors (validate-options incoming-options)
        combined-errors
          (str (when configure-errors
                 (str "Global configuration errors: " configure-errors))
               (when errors (str "Option errors in this call: " errors)))]
    (if (not (empty? combined-errors))
      (throw (Exception. combined-errors))
      ; remove set elements before doing anything else
      (let [[internal-map incoming-options _]
              (perform-remove nil nil (get-options) incoming-options)
            ; internal-map is the holder of the actual configuration
            ; as of this point
            [updated-map _ style-errors]
              (apply-style nil nil internal-map incoming-options)
            style-errors (when style-errors
                           (str "Option errors in this call: " style-errors))
            actual-options (if (not (empty? style-errors))
                             (throw (Exception. style-errors))
                             (merge-deep updated-map incoming-options))]
        #_(def dout actual-options)
        ; actual-options is a complete options-map
        actual-options))))


;;
;; # Configure all maps
;;

(defn config-and-validate-external
  "Configure from the zpstrc file on top of defaults.  Left in the
  structure from zprint's config-and-validate-all, in case we want
  to add some other options for validation at some later date.
  Returns [new-map doc-map errors]"
  []
  (let [default-map (get-default-options)
        default-doc-map (get-default-explained-all-options)
        ;
        ; $HOME/.zpstrc
        ;
        home (System/getenv "HOME")
        file-separator (System/getProperty "file.separator")
        zpstrc-file (str home file-separator zpstrc)
        [opts-rcfile errors-rcfile rc-filename]
          (when (and home file-separator)
            (get-config-from-file zpstrc-file :optional))
        [updated-map new-doc-map rc-errors] (config-and-validate
                                              (str "File: " zpstrc-file)
                                              default-doc-map
                                              default-map
                                              opts-rcfile)
        ;
        ; Process errors together
        ;
        all-errors (apply str
                     (interpose "\n" (filter identity (list errors-rcfile))))
        all-errors (if (empty? all-errors) nil all-errors)]
    [updated-map new-doc-map all-errors]))

;;
;; # Argument Manipulation
;;

(def display-keys [:docstring? :args? :call? :params? :name? :stack? :frame?])

(defn trim-last
  "Trim the last character off of a keyword."
  [sym]
  (keyword (subs (name sym) 0 (dec (count (name sym))))))

(defn convert-keys-to-set
  "Given a seq of boolean keys in a map, convert them individually
  to a set. By individually, that is to say that this a sequence of
  keys, not a key sequence."
  [ks m]
  (reduce #(if (%2 m) (conj %1 (trim-last %2)) %1) #{} ks))

(defn add-option-set
  "Given an option map, turn the display-keys into a set in the map."
  [m]
  (assoc m :show (convert-keys-to-set display-keys m)))

;;
;; # Help
;;

(defn vec-str-to-str
  "Take a vector of strings and concatenate them into one string with
  newlines between them."
  [vec-str]
  (apply str (interpose "\n" vec-str)))

;!zprint {:vector {:wrap? false}}

(def help-str
  (vec-str-to-str
    [(about)
     ""
     " zpst -- provide source information after an exception"
     ""
     " Basic use:"
     ""
     "   (zpst)"
     ""
     "   (zpst <exception> <depth> <options>)"
     ""
     " Gather additional information if you can reproduce the exception:"
     ""
     "   (collect)"
     ""
     " - reproduce the exception"
     ""
     "   (analyze)"
     ""
     " - choose a frame to examine"
     ""
     "   (set-frame! <depth>)"
     "   (show)"
     ""
     " - get and change arguments:"
     ""
     "   (get-arg <n>)"
     "   (set-arg! <n> <new-value>)"
     "   (restore-arg! <n>)"
     ""
     " - re-eval functions below the current frame:"
     ""
     "   (re-eval)"
     ""
     " - clear internal database and frame pointer (holds on to lots"
     "   of objects to allow (re-eval) to work)"
     ""
     "   (clear)"
     ""
     " Extended pst -- provide more information than pst, no sources:"
     ""
     "   (epst)"
     ""
     " Explain current configuration, shows source of non-default values:"
     ""
     "   (zpst :explain)"
     ""
     " Change current configuration from running code:"
     ""
     "   (set-options! <options-map>)"
     ""
     " Show this help text:"
     ""
     "   (zpst :help)"
     ""]))
