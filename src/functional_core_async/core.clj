(ns functional-core-async.core
  (:import java.util.concurrent.ArrayBlockingQueue))

;; CHANNELS
;; ========
(defn chan
  "Returns a new channel."
  ([] (chan 1 false))
  ([n fifo?] (ArrayBlockingQueue. n fifo?)))


(defn >!
  "Puts x on the channel. Thread safe."
  [^ArrayBlockingQueue ch x]
  (.put ch x))


(defn <!
  "Gets something off a channel. Thread safe."
  [^ArrayBlockingQueue ch]
  (.take ch))


;; ASYNC EVENT LOOP
;; ================
(defonce ^:private async-ch
  ;; 1M `go` blocks at a time
  (chan 1000000 false))


(defonce ^:private async-executor
  (future    ;; single threaded
    (loop [] ;; event loop
      (let [[f ok] (<! async-ch)] ;; pick job if available
        (try
          (ok (f)) ;; execute job and call the `ok` callback with the result
          (catch Exception e
            (.printStackTrace e)))) ;; log unhandled errors
      (recur))))


(defn schedule-async
  "Puts a job on the asynchronous job queue.
  `f`: a 0-arity fn to be run asynchronously
  `ok`: callback called with the result of (f)"
  [f ok]
  (>! async-ch [f ok])
  nil)


;; Lightweight 'Threads'
;; =====================
(defn go*
  "Evaluates (f) on the `async-executor` thread,
  returning a channel that gives the returned value."
  [f]
  (let [ch (chan)]
    (schedule-async f #(>! ch %))
    ch))


(defmacro go
  "Executes the body on the `async-executor` thread,
  returning a channel that gives the returned value."
  [& body]
  `(go*
    (fn []
      ~@body)))


;; Real Threads
;; ============
(defn thread*
  "Evaluates (f) on a separate thread, returning
  a channel that gives the returned value."
  [f]
  (let [ch (chan)]
    (future (>! ch (f)))
    ch))


(defmacro thread
  "Executes the body on a separate thread, returning 
  a channel that gives the returned value."
  [& body]
  `(thread*
    (fn []
      ~@body)))
