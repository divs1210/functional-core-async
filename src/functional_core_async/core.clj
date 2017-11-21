(ns functional-core-async.core
  (:import [java.util.concurrent ArrayBlockingQueue TimeUnit]))

;; CHANNELS
;; ========
(defn chan
  "Returns a new channel."
  ([]
   (chan 1))
  ([width]
   (chan width true))
  ([width fifo?]
   (ArrayBlockingQueue. width fifo?)))


(defn ^:private poll!
  "Returns value if available in given duration, or ::nil."
  [^ArrayBlockingQueue ch microseconds]
  (or (.poll ch microseconds TimeUnit/MICROSECONDS)
      ::nil))


(defn <!!
  "Gets something off the channel. Thread safe, blocking."
  [^ArrayBlockingQueue ch]
  (.take ch))


(defn >!!
  "Puts x on the channel. Thread safe, blocking."
  [^ArrayBlockingQueue ch x]
  (.put ch x))


(defmacro async-take
  "Executes body when something is received from the channel.
  Can be used only within `go` blocks!"
  [[v ch] & body]
  ^{:type ::async-take}
  {:ch ch
   :fn `(fn [~v]
          ~@body)})


(defmacro async-put
  "Executes body when v is put on the channel.
  Can be used only within `go` blocks!"
  [[ch v] & body]
  ^{:type ::async-put}
  {:ch ch
   :fn `(fn []
          ~@body)
   :val v})


;; ASYNC EVENT LOOP
;; ================
(def ^:private async-ch
  ;; 1M `go` blocks at a time
  (chan 1000000 false))


(defn ^:private schedule-async
  "Puts a job on the asynchronous job queue.
  `f`: a 0-arity fn to be run asynchronously
  `ok`: callback called with the result of (f)"
  [f ok]
  (>!! async-ch [f ok])
  nil)


(defn ^:private execute []
  (let [[f ok] (<!! async-ch)] ;; pick job if available
    (try
      (let [res (f)]
        (case (type res)
          ::async-take
          (let [chan (:ch res)]
            (let [v (poll! chan 10)]
              (if-not (= ::nil v)
                (ok ((:fn res) v))
                (schedule-async (fn [] res) ok))))

          ::async-put
          (let [chan (:ch res)
                val (:val res)]
            (if (pos? (.remainingCapacity (:ch @chan)))
              (do
                (>!! chan val)
                (ok ((:fn res) val)))
              (schedule-async (fn [] res) ok)))

          ;; else
          (ok res)))
      (catch Exception e
        (.printStackTrace e)))))


(def ^:private async-executor
  (future
    (loop []
      (execute)
      (recur))))


;; Lightweight Threads
;; ===================
(defn go*
  "Evaluates (f) on the `async-executor` thread,
  returning a channel that gives the returned value.
  If it takes more than 10ms, will be promoted to a
  separate thread."
  [f]
  (let [ch (chan)]
    (schedule-async f #(>!! ch %))
    ch))


(defmacro go
  "Executes the body on the `async-executor` thread,
  returning a channel that gives the returned value.
  If it takes more than 10ms, will be promoted to a
  separate thread."
  [& body]
  `(go*
    (fn []
      ~@body)))


;; POLLING OPS
;; ===========
(defn alts!
  "Listens on a bunch of channels, and returns
  [ch val] for the first thing that arrives."
  [chans]
  (let [p (promise)]
    (doseq [ch (cycle chans)
            :while (not (realized? p))
            :let [res (poll! ch 10)]]
      (when (not= ::nil res)
        (deliver p [res ch])))
    @p))


;; TIMEOUTS
;; ========
(defn timeout
  "Returns a channel that contains ::nil after
  the given duration in milliseconds."
  [ms]
  (let [ch (chan)]
    (future
      (Thread/sleep ms)
      ::nil)
    ch))
