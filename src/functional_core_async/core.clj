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


;; BLOCKING CHANNEL OPS
;; ====================
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


;; ASYNC CHANNEL OPS
;; =================
(defn <!
  "Executes body when something is received from the channel.
  Can be used only within `go` blocks!"
  [ch body-fn]
  ^{:type ::<!}
  {:ch ch
   :fn body-fn})


(defn >!
  "Executes body when v is put on the channel.
  Can be used only within `go` blocks!"
  [ch v body-fn]
  ^{:type ::>!}
  {:ch ch
   :fn body-fn
   :val v})


;; ASYNC EVENT LOOP
;; ================
(defonce ^:private async-ch
  ;; 1M `go` blocks at a time
  (chan 1000000 false))


(defn schedule-async
  "Puts a job on the asynchronous job queue.
  `f`: a 0-arity fn to be run asynchronously
  `ok`: callback called with the result of (f)"
  [f ok]
  (>!! async-ch [f ok])
  nil)


(defn ^:private execute
  "Wait for, pick up, and execute one job
  from the job queue."
  []
  (let [[f ok] (<!! async-ch)
        res (f)]
    (case (type res)
      ::<!
      (let [chan (:ch res)
            v (poll! chan 1000)]
        (if-not (= ::nil v)
          (ok ((:fn res) v))
          (schedule-async (fn [] res) ok)))

      ::>!
      (let [chan (:ch res)
            val (:val res)]
        (if (locking chan
              (pos? (.remainingCapacity chan)))
          (do
            (>!! chan val)
            (ok ((:fn res))))
          (schedule-async (fn [] res) ok)))

      ;; else
      (ok res))))


(defonce ^:private async-executors
  (let [cores (.availableProcessors (Runtime/getRuntime))]
    (doall
     (for [_ (range cores)]
       (future
         (while true
           (try
             (execute)
             (catch Exception e
               (.printStackTrace e)))))))))


;; Lightweight Threads
;; ===================
(defn go*
  "Evaluates (f) on the `async-executor` thread,
  returning a channel that gives the returned value."
  [f]
  (let [ch (chan)]
    (schedule-async f #(>!! ch %))
    ch))


(defmacro go
  "Executes the body on the `async-executor` thread,
  returning a channel that gives the returned value."
  [& body]
  `(go* (fn [] ~@body)))


(defmacro go<!
  "Takes something from ch and puts it in v.
  Returns a channel."
  [[v ch] & body]
  `(go (<! ~ch (fn [~v] ~@body))))


(defmacro go>!
  "Puts v on ch.
  Returns a channel."
  [[ch v] & body]
  `(go (>! ~ch ~v (fn [] ~@body))))


(defn goloop*
  "Calls (f gorecur initial-state).
  f can call (gorecur state) to loop."
  [f initial-state]
  (letfn [(gorecur [& [state]]
            (goloop* f state))]
    (go
      (f gorecur initial-state))))


(defmacro goloop
  "Like `loop`, but runs each iteration
  on a separate go block, and doesn't
  block the main thread.
  Call (gorecur state) in body to loop."
  [[var initial-state] & body]
  `(goloop* (fn [~'gorecur ~var]
              ~@body)
            ~initial-state))


(defn goconsume*
  "Calls (f v gorecur) when v is
  received on ch.
  f can call (gorecur) to wait for
  another v on ch."
  [ch f]
  (goloop [_ nil]
    (go<! [v ch]
      (f v gorecur))))


(defmacro goconsume
  "Like `go<!`, but body can call
  (gorecur) to loop."
  [[var ch] & body]
  `(goconsume* ~ch
               (fn [~var ~'gorecur]
                 ~@body)))
