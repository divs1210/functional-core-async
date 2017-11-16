(ns functional-core-async.protocols)

(defprotocol IBufferedChannel
  (take! [ch] "Gets something off the channel.")
  (put! [ch x] "Puts x on the channel.")
  (close! [ch] "Closes the channel."))
