(ns criterium.instrument
  "Add spec instrumentation.
  This is pretty costly, so shouldn't be always enabled."
  (:require
   [clojure.spec.alpha :as s]
   [criterium.budget :as budget]
   [criterium.measured :as measured]
   [expound.alpha :as expound]
   [orchestra.spec.test :as stest]))

(defn instrument-all []
  (stest/instrument
   [`budget/add
    `budget/subtract
    `budget/budget?
    `budget/budget
    `budget/budget*
    `measured/measured
    `measured/invoke
    `measured/expr
    `pipeline/time-metric
    `pipeline/with-class-loader-counts
    `pipeline/with-compilation-time
    `pipeline/with-memory
    `pipeline/with-runtime-memory
    `pipeline/with-finalization-count
    `pipeline/with-garbage-collector-stats
    `pipeline/elapsed-time
    `pipeline/total-memory
    `pipeline/divide
    `pipeline/pipeline
    `pipeline/execute]))

(defn explain-with-expound []
  (set! s/*explain-out* expound/printer))

(explain-with-expound)
;; (instrument-all)
