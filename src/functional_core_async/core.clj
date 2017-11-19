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
   (atom
    {:ch (ArrayBlockingQueue. width fifo?)
     :open? true
     :waiting-takes 0})))


(defn <!
  "Gets something off the channel. Thread safe, blocking."
  [chan]
  (let [{:keys [^ArrayBlockingQueue ch open? waiting-takes]} @chan]
    (when (or (seq ch) open?)
      (swap! chan update :waiting-takes inc)
      (let [res (.take ch)]
        (swap! chan update :waiting-takes dec)
        (when-not (= ::closed res)
          res)))))


(defn >!
  "Puts x on the channel. Thread safe, blocking."
  [chan x]
  (let [{:keys [^ArrayBlockingQueue ch open?]} @chan]
    (when (and (some? x) open?)
      (.put ch x)))
  nil)


(defn close!
  "All further puts will be ignored, takes will return nil.
  Thread safe, blocking."
  [chan]
  (let [{:keys [^ArrayBlockingQueue ch open? waiting-takes]} @chan]
    (when (or (seq ch) open?)
      (swap! chan assoc :open? false)
      (dotimes [_ waiting-takes]
        (.put ch ::closed))))
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


;; POLLING OPS
;; ===========
(defn ^:private poll!
  "Returns value if available in given duration, or ::nil."
  [chan microseconds]
  (let [{:keys [^ArrayBlockingQueue ch open?]} @chan]
    (when (or (seq ch) open?)
      (or (.poll ch microseconds TimeUnit/MICROSECONDS)
          ::nil))))


(defn select!
  "Listens on a bunch of channels, and returns
  [ch val] for the first thing that arrives."
  [chans]
  (let [p (promise)]
    (doseq [ch (cycle chans)
            :while (not (realized? p))
            :let [res (poll! ch 10)]]
      (when (not= ::nil res)
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


;; TIMEOUTS
;; ========
(defn timeout
  "Returns a channel that closes after the given
  duration in milliseconds."
  [ms]
  (let [ch (chan)]
    (future
      (Thread/sleep ms)
      (close! ch))
    ch))
