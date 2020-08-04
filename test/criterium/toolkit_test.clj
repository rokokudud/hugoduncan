(ns criterium.toolkit-test
  (:require [criterium
             [measured :as measured]
             [toolkit :as toolkit]]
            [clojure.test :refer [deftest is]]))


(def m (measured/measured
         (fn [] ::state)
         (fn [state _n]
           [::time [state state]])
         nil))

(deftest instrumented-test
  (is (= {:state      ::state
          :expr-value [::state ::state]
          :num-evals  1
          :time       :criterium.toolkit-test/time}
         (toolkit/instrumented
           m
           (toolkit/with-time)))))