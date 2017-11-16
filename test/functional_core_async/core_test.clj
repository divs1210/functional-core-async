(ns functional-core-async.core-test
  (:require [clojure.test :refer :all]
            [functional-core-async.core :refer :all]))

(deftest channel-test
  (let [ch (chan)]
    (>! ch 1)
    (is (= 1 (<! ch))
        "Channels block on write and read.")

    (close! ch)
    (>! ch 2)
    (is (= nil (<! ch))
        "Closed channels ignore puts and return nil on take.")))


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

;; NOTE: also check out `functional-core-async.examples`
