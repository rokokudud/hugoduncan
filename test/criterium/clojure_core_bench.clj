(ns criterium.clojure-core-bench
  (:require  [clojure.test.check
              [generators :as gen]]
             [criterium
              [arg-gen :as arg-gen]
              [measured :as measured]
              [measure :as measure]]))

;;; arg-gen a constant

(defn constant-bench [mx]
  (arg-gen/measured {:size mx} [i gen/small-integer]
    i))


(comment
  (criterium.measure/measure
    (constant-bench 10)
    {:limit-time 10})
  )

;;; Identity

(defmacro recursively
  "Expand to call  f recursively n times with the initial argument, init."
  [n f init]
  (reduce
    (fn [expr n]
      `(~f ~expr))
    init
    (range n)))

;; (defmacro measured-recursively
;;   [n f init]
;;   (dotimes [i n]
;;     (recursively)))

(def gen-for-identity (gen/vector gen/any))

(defn identity-bench0 [mx]
  (arg-gen/measured {:size mx} [i gen-for-identity]
    i))

(defn identity-bench [mx]
  (arg-gen/measured {:size mx} [i gen-for-identity]
    (identity i)))

(defn identity-bench2 [mx]
  (arg-gen/measured {:size mx} [i gen-for-identity]
    (recursively 2 identity i)))

(defn identity-bench3 [mx]
  (arg-gen/measured {:size mx} [i gen-for-identity]
    (recursively 3 identity i)))

(defn identity-bench4 [mx]
  (arg-gen/measured {:size mx} [i gen-for-identity]
    (recursively 4 identity i)))

(defn identity-regression []
  ;;(println :hello1)
  (let [ms [;; (identity-bench0 100)
            (identity-bench 100)
            (identity-bench2 100)
            (identity-bench3 100)
            (identity-bench4 100)]
        n  (count ms)
        xs (range 1 (inc n))
        ys (reduce
             (fn [res m]
               (conj res (-> (criterium.measure/measure
                              m
                              {:limit-time 10})
                            :stats :time :mean first)))
             []
             ms)
        lr (criterium.stats/linear-regression xs ys)
        ;; _ (println {:xs xs :ys ys})
        c  (criterium.chart/xy-chart xs ys)
        ]
    (println "Regression" lr)
    ;; (println {:xs xs :ys ys})
    ;; (println :hello)
     (criterium.chart/view c)
    ))

(comment
  (identity-regression)
  )




(comment
  (criterium.measure/measure
    (identity-bench 10000)
    {:limit-time 10}))

;;; inc

(defn inc-bench [mx]
  (arg-gen/measured {:size mx} [i (gen/choose 0 1000)]
    (inc i)))

(comment
  (criterium.measure/measure
    (inc-bench 10000)
    {:limit-time 10}))



;; nth

(comment
  (defn inc-depth [x] (vec (repeat 3 (vec (take 3 x)))))
  (defn v-n [depth]
    (let [v0 ['a 'b 'c]]
      (last (take depth (iterate inc-depth v0)))))

  (def v (v-n 5))

  (criterium.measure/measure
    (measured/expr
      (nth (nth (nth (nth v 1) 2) 0) 1))
    {:limit-time  10
     :limit-evals 1000000000})

  (criterium.measure/measure
    (measured/expr
      (nth (nth (nth v 1) 2) 0))
    {:limit-time  10
     :limit-evals 1000000000})  ;; 13.23813842438487

  (criterium.measure/measure
    (measured/expr
      (nth (nth v 1) 2))
    {:limit-time  10
     :limit-evals 1000000000})  ; 13.60222827768907

  (def v [0 1 2 3 4 5 6 7 8 9])
  (criterium.measure/measure
    (measured/expr
      (nth v 2)
      {:arg-metas [{} {:tag 'int}]}
      )
    {:limit-time  10
     :limit-evals 1000000000})

  (criterium.measure/measure
    (measured/expr
      (.nth v 2)
      #_{:arg-metas [{:tag clojure.lang.Indexed}
                   {:tag int}]})
    {:limit-time  10
     :limit-evals 1000000000})

  (criterium.measure/measure
    (measured/expr
      (.nth ^clojure.lang.Indexed (.nth v 2) 1)
      {:arg-metas [{:tag clojure.lang.Indexed}
                   {:tag int}
                   {:tag int}]})
    {:limit-time  10
     :limit-evals 1000000000})

(measured/symbolic
  (measured/expr
    (.nth ^clojure.lang.Indexed (.nth v 2) 1)
    {:arg-metas [{:tag clojure.lang.Indexed}
                 {:tag long}
                 {:tag long}]}))

  (criterium.measure/measure
    (measured/expr
      (.nth [0 1 2 3 4 5 6 7 8 9] 9)
      {:arg-metas [{:tag clojure.lang.Indexed}
                   {:tag long}]}
      )
    {:limit-time  10
     :limit-evals 1000000000}) ; 12.833147195100894

  (criterium.measure/measure
    (measured/expr
      (nth [0 1 2 3 4 5 6 7 8 9] 9)
      {:arg-metas [{:tag clojure.lang.Indexed}
                   {:tag long}]}
      )
    {:limit-time  10
     :limit-evals 1000000000})

  (defn nth-regression []
    ;;(println :hello1)
    (let [ms      [;; (identity-bench0 100)
                   (measured/expr
                     (nth v 1))
                   (measured/expr
                     (nth (nth v 1) 1))
                   (measured/expr
                     (nth (nth (nth v 1) 1) 1))
                   (measured/expr
                     (nth (nth (nth (nth v 1) 1) 1) 1))
                   ]
          n       (count ms)
          xs      (range 1 (inc n))
          ys      (reduce
               (fn [res m]
                 (conj res (-> (criterium.measure/measure
                                m
                                {:limit-time 10})
                              :stats :time :mean first)))
               []
               ms)
          [a0 a1] (criterium.stats/linear-regression xs ys)
          ;; _ (println {:xs xs :ys ys})
          c       (criterium.chart/xy-chart xs ys)
          ]
      (println "Regression" [a0 a1])
      ;; (println {:xs xs :ys ys})
      ;; (println :hello)
      (criterium.chart/view c)))

  ;; (nth-regression)

  (require 'no.disassemble)

  (println
    (no.disassemble/disassemble-str
      (:f (measured/expr
            (nth [[1 2 3] [2 3 1] [3 2 1]] 2)))))

  (println
    (no.disassemble/disassemble-str
      (:f (measured/expr
            (nth (nth v 1) 2)))))
  (println
    (no.disassemble/disassemble-str
      (:f (measured/expr
            (nth v 1)
            {:arg-metas [{} {:tag 'long}]}
            ))))


  (println
    (no.disassemble/disassemble-str
      (:f (measured/expr
            (.nth v 2)
            #_{:arg-metas [{:tag clojure.lang.Indexed}
                         {:tag long}]}
            ))))

    (println
    (no.disassemble/disassemble-str
      (fn [] (.nth [1 2 3] 1)) ))

    (println
    (no.disassemble/disassemble-str
      (fn [n]
        (let [^long n n]
          (.nth [1 2 3] 1))) ))

    (defmacro sym-meta [s]
      (println (type (:tag (meta s)))))

    (sym-meta ^long n)

)



(defn nth-bench [mx]
  (arg-gen/measured
    {:size mx}
    [v (gen/vector gen/int 1 1000)
     i (gen/choose 0 (dec (count v)))]
    (nth v i)
    ;; (+
    ;;   (nth v ^long i)
    ;;    ;; (nth v ^long (- i 1))
    ;;    ;; (nth v ^long (- i 2))
    ;;    )
    ))

(comment
  (criterium.measure/measure
    (nth-bench 10000)
    {:limit-time 10})

  (println
    (no.disassemble/disassemble-str
      (:f (nth-bench 1000))))q)

;; +
;; 3 133.27992622571227 59
;; 2 74.414102064389   54
;; 1 20.50997218893159
;; ll   16.888833639012905

;; make this a direct linked function, so we don't
;; end up with getRawRoot calls inside the timing loop.
(defn ^:const vec-nth [^clojure.lang.Indexed v i]
  (.nth v i))

(comment
  (println
    (no.disassemble/disassemble-str
      vec-nth)))

(defn vec-nth-bench [mx]
  (arg-gen/measured
    {:size mx}
    [v (gen/vector gen/int 1 1000)
     i (gen/choose 0 (dec (count v)))]
    ;; (vec-nth v i)
    (.nth  v i)
    ))

;; (defn vec-nth-bench [mx]
;;   (arg-gen/measured [v (gen/vector gen/int 1 mx)
;;                     i (gen/choose 0 (dec (count v)))]
;;     (vec-nth v i)))

(comment
  (criterium.measure/measure
    (nth-bench 10000)
    {:limit-time 10})

  (criterium.measure/measure
    (vec-nth-bench 10000)
    {:limit-time 10})


  (println
    (no.disassemble/disassemble-str
      (:f (nth-bench 10000))))

  (criterium.measure/measure
    (nth-bench 10)
    {:limit-time 10})

  (criterium.measure/measure
    (vec-nth-bench 10000)
    {:limit-time 10})

  (println
    (no.disassemble/disassemble-str
      (:f (vec-nth-bench 10000))))
  (println
    (no.disassemble/disassemble-str
      (:f (measured/expr (.nth [0 1 2 3] 3)))))

  (println
    (no.disassemble/disassemble-str
      (let [x (clojure.java.api.Clojure/read "(fn [v i] (nth v i))")]
        (eval x))))

  (println
    (no.disassemble/disassemble-str
      (let [x (clojure.java.api.Clojure/read "(fn [v i] (.nth ^clojure.lang.Indexed v i))")]
        (eval x))))

  (criterium.measure/measure
    (vec-nth-bench 10)
    {:limit-time 10})

  (criterium.measure/measure
    (measured/expr
      (.nth [0 1 2 3] 3))
    {:limit-time 10}))


(defn vector-destructure-bench [mx]
  (arg-gen/measured
    {:size mx}
    [v (gen/vector gen/int 3 1000)
     i (gen/choose 0 (dec (count v)))]
    (let [[a b c] v]
      (+ a b c))))

(defn vector-explicit-destructure-bench [mx]
  (arg-gen/measured
    {:size mx}
    [v (gen/vector gen/int 3 1000)
     i (gen/choose 0 (dec (count v)))]
    (+ (.nth ^clojure.lang.IPersistentVector v 0)
       (.nth ^clojure.lang.IPersistentVector v 1)
       (.nth ^clojure.lang.IPersistentVector v 2))))



(comment
  (let [v        [1 2 3]
        state-fn (fn [] v)
        f        (fn [^clojure.lang.APersistentVector v ^long eval-count]
                   (let )
                   (.nth  v  2))
        measured (measured/measured state-fn f 1)
        ;; measured (measured/expr (.nth [1 2 3] 2))
        ]
    (def m measured)
    (criterium.measure/measure
      measured
      {:limit-time  10
       :limit-evals 10000000000}))

  (def mb (measured/batch m 100000 ;; {:sink-fn criterium.eval/sink-primitive-long}
                          ))
  (dotimes [i 1000]
    (->
      (criterium.toolkit/sample
        (criterium.pipeline/time-metric)
        mb)
      :time
      (/ 100000.0)))

  (dotimes [i 100000]
    (->
      (criterium.toolkit/sample
        (criterium.pipeline/time-metric)
        m)
      :time))

  (let [v        [1 2 3]
        state-fn (fn [] v)
        f        (fn [v]
                   (nth v 2))
        measured (measured/measured state-fn f 1)]
    (def m measured)
    (criterium.measure/measure
      measured
      {:limit-time  10
       :limit-evals 10000000000}))

  (criterium.measure/measure
    m
    {:limit-time  10
     :limit-evals 10000000000})


  (criterium.measure/measure
    (criterium.toolkit/measured-batch
      (vector-destructure-bench 3)
      1000)
    {:limit-time 10})

  (criterium.measure/measure
    (vector-explicit-destructure-bench 3)
    {:limit-time 10})


  (criterium.chart/view
    (criterium.chart/time-histogram
      (criterium.measure/measure
        (vec-nth-bench 10000)
        {:limit-time     1
         :return-samples true}))))


(comment
  (require '[criterium.core :refer [report-result benchmark]])
  (let [v [1 2 3]]
    (report-result
      (benchmark
        (.nth ^IPersistentVector v 2) {})))) ;; 4.049624 ns

(comment
  (let [v [1 2 3]]
    (report-result
      (benchmark
        (nth v 2) {})))) ;; 7.456217 ns
