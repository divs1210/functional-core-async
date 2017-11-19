(ns functional-core-async.core-test
  (:require [clojure.test :refer :all]
            [functional-core-async.core :refer :all]))

(deftest channel-test
  (let [ch (chan)]
    (>! ch 1)
    (is (= 1 (<! ch))
        "Channels block on write and read."))

  (let [ch (chan)
        state (atom [])]
    (dotimes [_ 3]
      (future
        (swap! state conj (<! ch))))
    (>! ch 1)
    (close! ch)
    (>! ch 2)
    (Thread/sleep 100)
    (is (= [1 nil nil] @state)
        "All puts after close! are ignored, takes return nil.")))


(deftest scheduler-test
  (let [ch (chan)]
    (schedule-async #(inc 1)
                    #(>! ch %))
    (is (= 2 (<! ch))
        "Channel should have return value.")))


(deftest go-test
  (let [ch (chan)
        res (promise)]
    (go (deliver res (<! ch)))
    (>! ch 1)
    (is (= 1 @res)
        "go blocks should be async.")))


(deftest alts-test
  (let [res (promise)]
    (go
      (alts!
       {(chan)         #(deliver res [:ch %])
        (timeout 1000) #(deliver res [:to %])}))
    (is (= [:to nil] @res)
        "alts! executes timeout function."))

  (let [res (promise)
        ch (chan)]
    (go
      (alts!
       {ch             #(deliver res [:ch %])
        (timeout 1000) #(deliver res [:to %])}))
    (>! ch "hi")
    (is (= [:ch "hi"] @res)
        "alts! executes channel function.")))

;; NOTE: also check out `functional-core-async.examples`
