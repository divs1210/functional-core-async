(ns functional-core-async.core
  (:import java.util.LinkedList))

;; CHANNELS
;; ========
(defn chan
  "Returns a new unbounded channel."
  []
  (LinkedList.))


(defn chan-state
  "Returns the state of the channel.
  `:free` available to read/write
  `:blocked` blocked on read
  `:empty` available to write, but will block on read"
  [ch]
  (locking ch
    (cond
      (and (-> ch first map?)
           (-> ch first ::ch-promise))
      :blocked

      (seq ch)
      :free

      :else
      :empty)))


(defn >!
  "Puts x on the channel. Thread safe?"
  [ch x]
  (cond
    ;; not blocked
    (not= (chan-state ch) :blocked)
    (.addLast ch x)

    ;; blocked on read
    (-> ch first ::ch-promise realized? not)
    (-> ch first ::ch-promise (deliver x))

    ;; blocked on read, but free to write
    :else
    (.addLast ch x)))


(defn <!
  "Gets something off a channel. Thread safe?"
  [ch]
  (case (chan-state ch)
    :free
    (.pop ch)

    :blocked
    (let [res @(-> ch first ::ch-promise)]
      (.pop ch)
      res)

    :empty
    (do
      (.push ch {::ch-promise (promise)})
      (recur ch)))) ;; goto :blocked case


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
