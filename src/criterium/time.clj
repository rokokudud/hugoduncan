(ns criterium.time
  "Provides an augmented time macro for simple timing of expressions"
  (:refer-clojure :exclude [time])
  (:require [criterium
             [eval :as eval]
             [format :as format]
             [jvm :as jvm]
             [stats :as stats]
             [toolkit :as toolkit]
             [well :as well]]))

(def last-time* (volatile! nil))

(def metrics
  {:time              `toolkit/with-time
   :garbage-collector `toolkit/with-garbage-collector-stats
   :finalization      `toolkit/with-finalization-count
   :memory            `toolkit/with-memory
   :runtime-memory    `toolkit/with-runtime-memory
   :compilation       `toolkit/with-compilation-time
   :class-loader      `toolkit/with-class-loader-counts})

(defmulti format-metric (fn [metric val] metric))

(defmethod format-metric :time
  [_ val]
  (let [v (/ val 1e9)]
    (format "%32s: %s\n" "Elapsed time" (format/format-value :time v))))

(defn- format-count-time [[k {c :count t :time}]]
  (format "%36s:  count %d  time %s\n" (name k) c (format/format-value :time t)))

(defmethod format-metric :garbage-collector
  [_ val]
  (format "%32s:\n%s" "Garbage collection"
       (apply str (map format-count-time val))))

(defmethod format-metric :finalization
  [_ val]
  (format "%32s: %d\n" "Pending finalisations" (:pending val)))

(defn- format-memory-metrics [[k vs]]
  (apply
    str
    (format "%36s:\n" (name k))
    (for [[mk v] vs]
      (format "%40s: %%s\n" (name mk) (format/format-value :memory v)))))

(defmethod format-metric :memory
  [_ val]
  (format "%32s:\n%s" "Memory"
          (apply str (map format-memory-metrics val))))

(defn- format-runtime-memory-metrics [[k v]]
  (format "%36s: %s\n" (name k) (format/format-value :memory v)))

(defmethod format-metric :runtime-memory
  [_ val]
  (format "%32s:\n%s" "Runtime Memory"
          (apply str (map format-runtime-memory-metrics val))))

(defmethod format-metric :compilation
  [_ val]
  (let [v (:compilation-time val)]
    (format "%32s: %s\n" "JIT Compilation time" (format/format-value :time v))))

(defn format-count [[k v]]
  (format "%36s: %d\n" (name k) v))

(defmethod format-metric :class-loader
  [_ val]
  (apply
    str
    (format "%32s:\n" "Classloader")
    (map format-count val)))

(defmethod format-metric :state
  [_ _]
  "")

(defmethod format-metric :expr-value
  [_ _]
  "")

(defmethod format-metric :num-evals
  [_ _]
  "")

(defn- wrap-for-metric [expr stat]
  `(~(metrics stat) ~expr))

(defn print-metrics [metrics]
  (doseq [[k v] metrics]
    (print (format-metric k v))))


;; (let [{:keys [f state-fn]} (measured-expr (inc 1))]
;;   (println :f f)
;;   (println :state-fn state-fn (state-fn))
;;   (f (state-fn)))


(defn sample-stats [batch-size samples opts]
  (let [sum           (toolkit/total samples)
        num-evals     (:num-evals sum)
        avg           (toolkit/divide sum num-evals)
        times         (mapv toolkit/elapsed-time samples)
        tail-quantile (:tail-quantile opts 0.025)
        stats         (stats/bootstrap-bca
                        (mapv double times)
                        (juxt
                          stats/mean
                          stats/variance
                          (partial stats/quantile 0.5)
                          (partial stats/quantile tail-quantile)
                          (partial stats/quantile (- 1.0 tail-quantile)))
                        (:bootstrap-size opts 100)
                        [0.5 tail-quantile (- 1.0 tail-quantile)]
                        well/well-rng-1024a)
        ks [:mean :variance :median :0.025 :0.975 ]
        stats (zipmap ks stats)
        scale-1 (fn [[p [l u]]]
                   [(/ p batch-size)
                    [(/ l batch-size) (/ u batch-size)]])
        scale-2 (fn [[p [l u]]]
                   [(/ p batch-size)
                    [(/ l batch-size) (/ u batch-size)]]) ;; TODO FIXME
        scale-fns {:mean scale-1
                   :variance scale-2
                   :median scale-1
                   :0.025 scale-1
                   :0.975 scale-1}]

    {:num-evals   num-evals
     :avg         avg
     :stats       (zipmap ks
                          (mapv
                            (fn [k]
                              ((scale-fns k) (k stats)))
                            ks))
     :samples     samples
     :num-samples (count samples)}))

(def DEFAULT-TIME-BUDGET-NS
  "Default time budget when no limit specified.
  100ms should be imperceptible."
  (* 100 toolkit/MILLISEC-NS))


(defn- pipeline-args [options]
  (let [use-metrics    (let [use-metrics (:metrics options [])]
                         (if (= :all use-metrics)
                           (keys toolkit/measures)
                           use-metrics))
        pipeline-options (if ((set use-metrics) :with-expr-value)
                           {:terminal-fn toolkit/with-expr-value})]
    [(vec (remove #{:with-expr-value :with-time} use-metrics))
     pipeline-options]))

(defn- pipeline-for-options [options]
  (let [use-metrics    (let [use-metrics (:metrics options [])]
                         (if (= :all use-metrics)
                           (keys toolkit/measures)
                           use-metrics))
        pipeline-options (if ((set use-metrics) :with-expr-value)
                           {:terminal-fn toolkit/with-expr-value})]
    (toolkit/pipeline
      (remove #{:with-expr-value :with-time} use-metrics)
      pipeline-options)))

(defn measure-stats
  "Evaluates measured and return metrics on its evaluation.

  The :metrics option accepts either :all, for all metrics, or a sequence of metric
  keyword selectors. Valid metrics
  are :time, :garbage-collector, :finalization, :memory, :runtime-memory,
  :compilation, and :class-loader."
  [{:keys [eval-count] :as measured}
   {:keys [limit-evals limit-time max-gc-attempts]
    :as   options}]
  (let [time-budget-ns           (if limit-time
                                   (long (* limit-time toolkit/SEC-NS))
                                   DEFAULT-TIME-BUDGET-NS)
        eval-budget              (or limit-evals 10000)
        [use-metrics pl-options] (pipeline-args options)
        pipeline                 (toolkit/pipeline
                                   use-metrics
                                   options)
        _                        (toolkit/force-gc (or max-gc-attempts 3))
        estimate                 (toolkit/estimate-execution-time
                                   measured
                                   {:time-budget-ns (quot time-budget-ns 4)
                                    :eval-budget    (quot eval-budget 4)})
        _ (println "estimate" estimate)
        time-budget-ns           (- time-budget-ns (:sum estimate))
        eval-budget              (- eval-budget (:num-evals estimate))
        vals                     (if (= toolkit/with-expr-value
                                        (:terminal-fn pl-options))
                                   (toolkit/sample-no-time
                                     measured
                                     pipeline
                                     {:eval-budget    eval-budget})
                                   (toolkit/sample
                                     measured
                                     pipeline
                                     {:time-budget-ns time-budget-ns
                                      :eval-budget    eval-budget}))]

    (sample-stats eval-count vals {})))

(defn measure-point-value
  "Evaluates measured and return metrics on its evaluation.

  The :metrics option accepts either :all, for all metrics, or a sequence of metric
  keyword selectors. Valid metrics
  are :time, :garbage-collector, :finalization, :memory, :runtime-memory,
  :compilation, and :class-loader."
  [measured {:keys [max-gc-attempts metrics terminsl-fn]
             :as   options}]
  (let [time-budget-ns DEFAULT-TIME-BUDGET-NS
        eval-budget    10000
        [use-metrics pl-options] (pipeline-args options)
        pipeline                 (toolkit/pipeline
                                   use-metrics
                                   options)
        ;; let functions allocate on initial invocation
        _              (toolkit/invoke-measured measured)
        _              (toolkit/force-gc (or max-gc-attempts 3))
        vals           (if (= toolkit/with-expr-value
                              (:terminal-fn pl-options))
                         (toolkit/sample-no-time
                           measured
                           pipeline
                           {:eval-budget eval-budget})
                         (toolkit/sample
                           measured
                           pipeline
                           {:time-budget-ns time-budget-ns
                            :eval-budget    eval-budget}))]
    (last vals)))

(defn measure*
  "Evaluates measured and return metrics on its evaluation.

  The :metrics option accepts either :all, for all metrics, or a sequence of metric
  keyword selectors. Valid metrics
  are :time, :garbage-collector, :finalization, :memory, :runtime-memory,
  :compilation, and :class-loader."
  [measured {:keys [limit-evals limit-time max-gc-attempts metrics]
             :as   options}]
  (if (or limit-evals limit-time)
    (measure-stats measured options)
    (measure-point-value measured options)))

(defmacro measure
  "Like time, but returns measurements rather than printing them."
  [expr & options]
  (let [options (apply hash-map options)]
    `(measure* (toolkit/measured-expr ~expr) ~options)))


(defn time*
  "Evaluates expr and prints the time it took.
  Return the value of expr.

  The timing info is available as a data structure by calling last-time.

  The :times option can be passed an integer specifying how many times to evaluate the
  expression.  The default is 1.  If a value greater than 1 is specified, then
  the value of the expression is not returned.

  The :metrics option accepts either :all, for all metrics, or a sequence of metric
  keyword selectors. Valid metrics
  are :time, :garbage-collector, :finalization, :memory, :runtime-memory,
  :compilation, and :class-loader."
  [measured options]
  (let [deltas (measure* measured options)]
    (vreset! last-time* deltas)
    (print-metrics deltas)
    (:expr-value deltas)))

(defmacro time
  "Evaluates expr and prints the time it took.
  Return the value of expr.

  The timing info is available as a data structure by calling last-time.

  The :times option can be passed an integer specifying how many times to evaluate the
  expression.  The default is 1.  If a value greater than 1 is specified, then
  the value of the expression is not returned.

  The :metrics option accepts either :all, for all metrics, or a sequence of metric
  keyword selectors. Valid metrics
  are :time, :garbage-collector, :finalization, :memory, :runtime-memory,
  :compilation, and :class-loader."
  [expr & options]
  (let [options          (apply hash-map options)
        ;; n                (:times options 1)
        ;; use-metrics      (let [use-metrics (:metrics options [:time])]
        ;;                    (if (= :all use-metrics) (keys metrics) use-metrics))
        ;; measured-fn-form (measured-expr expr)
        ;; f-sym            (gensym "f")
        ;; state-sym        (gensym "state")
        ]
    `(time* (toolkit/measured-expr ~expr) ~options)
    ;; `(let [measured-fn# ~measured-fn-form
    ;;        state-fn#    (:state-fn measured-fn#)
    ;;        ~state-sym   (state-fn#)
    ;;        ~f-sym       (:f measured-fn#)
    ;;        vals#        (toolkit/instrumented
    ;;                       ~(reduce wrap-for-metric
    ;;                                (if (= 1 n)
    ;;                                  `(toolkit/with-expr-value (~f-sym ~state-sym))
    ;;                                  `(toolkit/with-expr
    ;;                                     (eval/execute-n-times (~f-sym ~state-sym) ~n)))
    ;;                                use-metrics))
    ;;        deltas#      (toolkit/deltas (dissoc vals# :expr-value))]
    ;;    (vreset! last-time* deltas#)
    ;;    (print-metrics deltas#)
    ;;    (:expr-value vals#))
    ))

(time (inc 1))


(defn last-time
  "Return the data from the last time invocation."
  []
  @last-time*)


;; (time (inc 2) :metrics :all)
;; (time (inc 2))
;; (time (inc 2) :times 5000)
;; (time (Thread/sleep 1))
;; (time (Thread/sleep 1) :times 5000)

;; (clojure.pprint/pprint (last-time))

;; (defn time-vs-n
;;   []
;;   (let [ns [;; 1 10 100 1000
;;             10000 100000 1000000 10000000 100000000
;;             ]
;;         ts (for [n ns]
;;              (do
;;                (time (inc 2) :times n)
;;                (-> (last-time) :time :elapsed (/ n))))]
;;     (criterium.chart/view (criterium.chart/chart ns (vec ts)))))

;; ;; (time-vs-n)

;; (let [n 100]
;;   (time (inc 2) :times n)
;;   (-> (last-time) :time :elapsed ;; (/ (double n))
;;       ))


;; (* 1e9 (/ 1 2.2e9))


;; (bench/for [x (gen/choose 0 100000)]
;;   (timw (inc x)))