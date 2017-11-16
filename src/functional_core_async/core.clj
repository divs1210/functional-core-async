(ns functional-core-async.core
  (:require [functional-core-async.protocols :as p])
  (:import java.util.concurrent.ArrayBlockingQueue))

;; CHANNELS
;; ========
(defn chan
  "Returns a new channel."
  ([] (chan 1))
  ([n] (chan n true))
  ([n fifo?]
   (let [ch (ArrayBlockingQueue. n fifo?)
         open? (atom true)]
     (reify
       clojure.lang.ISeq
       (seq [_]
         (seq ch))
       p/IBufferedChannel
       (take! [_]
         (when (or (seq ch) @open?)
           (let [res (.take ch)]
             (when (not= res ::closed)
               res))))
       (put! [_ x]
         (when (and x @open?)
           (.put ch x)))
       (close! [_]
         (when @open?
           (.put ch ::closed)
           (reset! open? false))
         nil)))))


(def ^{:doc "Gets something off a channel. Thread safe, blocking."}
  <! p/take!)

(def ^{:doc "Puts something on a channel. Thread safe, blocking."}
  >! p/put!)

(def ^{:doc "All further puts will be ignored, takes will return nil.
Thread safe, blocking."}
  close! p/close!)


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
