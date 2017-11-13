(ns functional-core-async.core
  (:import java.util.LinkedList))

;; CHANNELS
;; ========
(defn chan
  "Returns a new unbounded channel."
  []
  (LinkedList.))


(defn >!
  "Puts something on a channel. Thread safe."
  [ch x]
  (locking ch
    (.addLast ch x)))


(defn <!
  "Gets something off a channel. Thread safe."
  [ch]
  (if (locking ch (seq ch))
    (.pop ch)
    (do
      (Thread/sleep 1)
      (recur ch))))


;; ASYNC EVENT LOOP
;; ================
(defonce ^:private async-ch
  (chan))


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
