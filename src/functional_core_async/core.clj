(ns functional-core-async.core)

;; CHANNELS
;; ========
(defn chan
  "Returns a new unbounded channel."
  []
  (atom []))


(defn >!
  "Puts something on a channel. Thread safe."
  [ch x]
  (let [old-ch @ch
        new-ch (if (seq old-ch)
                 (conj old-ch x)
                 [x])]
    (if (compare-and-set! ch old-ch new-ch)
      new-ch
      (do
        (Thread/sleep 1)
        (recur ch x)))))


(defn <!
  "Gets something off a channel. Thread safe."
  [ch]
  (let [old-ch @ch
        new-ch (-> old-ch rest vec)
        x (first old-ch)]
    (if (and (seq old-ch)
             (compare-and-set! ch old-ch new-ch))
      x
      (do
        (Thread/sleep 1)
        (recur ch)))))


;; ASYNC EVENT LOOP
;; ================
(defonce ^:private async-ch
  (chan))


(defonce ^:private async-executor
  (future    ;; single threaded
    (loop [] ;; event loop
      (let [[f ok err] (<! async-ch)] ;; pick job if available
        (try
          (ok (f)) ;; execute job and call the `ok` callback with the result
          (catch Exception e
            (when err     ;; if something went wrong and an error callback
              (err e))))) ;; was provided, call it with the Exception
      (recur))))


(defn schedule-async
  "Puts a job on the asynchronous job queue.
  `f`: a 0-arity fn to be run asynchronously
  `ok`: callback called with the result of (f)
  `err`: optional callback called with the Exception"
  [f ok & [err]]
  (>! async-ch [f ok err])
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
