(ns amalloy.utils.seq
  (:use amalloy.utils
        [clojure.walk :only [postwalk-replace]]))

(defcomp iterations
  "Return a sequence of (f start), (f (f start))...until nil is
  encountered. Like clojure.core/iterate, but doesn't include the
  original element and doesn't go on forever."
  [f start]
  trim-seq rest iterate)

(defcomp ffilter
  "Like clojure.core/some, but instead of returning (pred x) for the
  matching element x, it returns x. Useful for predicates that return
  true/false instead of their argument."
  [pred coll]
  first filter)

(defn alternates
  "Split coll into 'threads' subsequences (defaults to 2), feeding
each alternately from the input sequence. Effectively the inverse of
interleave:

    (alternates 3 (range 9))
;=> ((0 3 6) (1 4 7) (2 5 8))"
  ([coll] (alternates 2 coll))
  ([threads coll]
   (for [offset (range threads)]
     (take-nth threads
               (drop offset coll)))))

(defmacro lazy-loop
  "Provide a simplified version of lazy-seq to eliminate
  boilerplate. Arguments are as to the built-in (loop...recur),
  and (lazy-recur) will be defined for you. However, instead of doing
  actual tail recursion, lazy-recur trampolines through lazy-seq. In
  addition to enabling laziness, this means you can call lazy-recur
  when not in the tail position."
  [bindings & body]
  (let [inner-fn 'lazy-recur
        [names values] (alternates bindings)]
    `((fn ~inner-fn
        ~(vec names)
        (lazy-seq
         ~@body))
      ~@values)))

(defn unfold
  "Traditionally unfold is the 'opposite of reduce': it turns a single
  seed value into a (possibly infinite) lazy sequence of output
  values.

  Next and done? are functions that operate on a seed. next should
  return a pair, [value new-seed]; the value half of the pair is
  inserted into the resulting list, while the new-seed is used to
  continue unfolding. Notably, the value is never passed as an
  argument to either next or done?.

  If done? is omitted, the sequence will be unfolded forever, for
  example
  (defn fibs []
    (unfold (fn [[a b]]
              [a [b (+ a b)]])
            [0 1]))"
  ([next seed]
     (unfold next (constantly false) seed))
  ([next done? seed]
     (lazy-loop [seed seed]
       (when-not (done? seed)
         (let [[value new-seed] (next seed)]
           (cons value
                 (lazy-recur new-seed)))))))

(defn take-shuffled
  "Lazily take (at most) n elements at random from coll, without
  replacement. For n=1, this is equivalent to rand-nth; for n>=(count
  coll) it is equivalent to shuffle.

  Clarification of \"without replacement\": each index in the original
  collection is chosen at most once. Thus if the original collection
  contains no duplicates, neither will the result of this
  function. But if the original collection contains duplicates, this
  function may include them in its output: it does not do any
  uniqueness checking aside from being careful not to use the same
  index twice."
  [n coll]
  (let [coll (vec coll)
        n (min n (count coll))]
    (take n
          (lazy-loop [coll coll]
            (let [idx (rand-int (count coll))
                  val (coll idx)
                  coll (-> coll
                           (assoc idx (peek coll))
                           pop)]
              (cons val (lazy-recur coll)))))))

(defn remove-once
  "Remove from coll first element which satisfies pred."
  [pred coll]
  (let [[before [x & after]] (split-with (complement pred) coll)]
    (concat before after)))
