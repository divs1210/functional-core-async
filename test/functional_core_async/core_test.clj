(ns functional-core-async.core-test
  (:require [clojure.test :refer :all]
            [functional-core-async.core :refer :all]))

(deftest channel-test
  (let [ch (chan)]
    (>!! ch 1)
    (is (= 1 (<!! ch))
        "Channels block on write and read.")))


(deftest scheduler-test
  (let [ch (chan)]
    (schedule-async #(inc 1)
                    #(>!! ch %))
    (is (= 2 (<!! ch))
        "Channel should have return value.")))


(deftest go-test
  (let [ch (chan)
        res (promise)]
    (go (deliver res (<!! ch)))
    (>!! ch 1)
    (is (= 1 @res)
        "go blocks should be async.")))


(deftest alts-test
  (let [ch (chan)
        to (timeout 1000)
        [v c] (alts! [ch to])
        res (chan)]
    (condp = c
      ch (>!! res :ch)
      to (>!! res :to))
    (is (= :to (<!! res))
        "alts! returns value and channel for the first value that arrives.")))

;; NOTE: also check out `functional-core-async.examples`
