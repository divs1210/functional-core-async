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
   {:ch (ArrayBlockingQueue. width fifo?)
    :open? (atom true)}))


(defn <!
  "Gets something off the channel. Thread safe, blocking."
  [{:keys [^ArrayBlockingQueue ch open?]}]
  (when (or (seq ch) @open?)
    (let [res (.take ch)]
      (when (not= res ::closed)
        res))))


(defn >!
  "Puts x on the channel. Thread safe, blocking."
  [{:keys [^ArrayBlockingQueue ch open?]} x]
  (when (and x @open?)
    (.put ch x)))


(defn close!
  "All further puts will be ignored, takes will return nil.
  Thread safe, blocking."
  [{:keys [^ArrayBlockingQueue ch open?]}]
  (when @open?
    (.put ch ::closed)
    (reset! open? false))
  nil)


;; ASYNC EVENT LOOP
;; ================
(defonce ^:private async-ch
  ;; 1M `go` blocks at a time
  (chan 1000000 false))


(defn ^:private async-executor
  "Starts an execution thread and a monitor thread.
  If the currently executing task takes more than 10ms,
  the monitor will retire the execution thread, and
  it will be dedicated to the task at hand, after which
  both these threads will die."
  []
  (let [retired? (promise)
        check-status? (chan)
        status-promise (atom nil)]
    (future ;; monitor thread
      (while (not (realized? retired?))
        (when (<! check-status?)
          (let [status (deref @status-promise 10 :timeout)]
            (when (= :timeout status)
              (deliver retired? true))))))
    (future ;; execution thread
      (while (not (realized? retired?))
        (let [[f ok] (<! async-ch)]   ;; pick job if available
          (reset! status-promise (promise))
          (>! check-status? true)
          (try
            (ok (f)) ;; execute job and call the `ok` callback with the result
            (catch Exception e
              (.printStackTrace e)))
          (deliver @status-promise :done))))
    retired?))


(defonce ^:private supervised-async-executor
  (future ;; starts a new executor if the existing one retires
    (loop [retired? (async-executor)]
      @retired?
      (recur (async-executor)))))


(defn schedule-async
  "Puts a job on the asynchronous job queue.
  `f`: a 0-arity fn to be run asynchronously
  `ok`: callback called with the result of (f)"
  [f ok]
  (>! async-ch [f ok])
  nil)


;; Lightweight Threads
;; ===================
(defn go*
  "Evaluates (f) on the `async-executor` thread,
  returning a channel that gives the returned value.
  If it takes more than 10ms, will be promoted to a
  separate thread."
  [f]
  (let [ch (chan)]
    (schedule-async f #(>! ch %))
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


;; ALTS!
;; =====
(defn ^:private poll!
  [{:keys [^ArrayBlockingQueue ch open?]} microseconds]
  (when @open?
    (let [res (.poll ch microseconds TimeUnit/MICROSECONDS)]
      (when (not= res ::closed)
        res))))


(defn select!
  "Listens on a bunch of channels, and returns
  [ch val] for the first thing that arrives."
  [chans]
  (let [p (promise)]
    (doseq [ch (cycle chans)
            :while (not (realized? p))
            :let [res (poll! ch 10)]]
      (when res
        (deliver p [ch res])))
    @p))


(defn alts!
  "Takes a map of channels -> functions.
  Listens on channels, and if a value `val`
  is recieved on some `ch`, calls (`f` `val`)
  with the corresponding function `f`."
  [chan-fn-map]
  (let [chans (keys chan-fn-map)
        [ch val] (select! chans)
        f (get chan-fn-map ch)]
    (f val)))
