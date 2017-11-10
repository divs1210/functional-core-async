(ns functional-core-async.examples
  (:require [functional-core-async.core
             :refer [chan >! <! schedule-async go]]))

;; SETUP
;; =====
;; remote db
(defonce db
  {:user0 "callback"
   :user1 "channel"
   :user2 "channel + go"})

;; query fn
(defn get-user-from-db
  "Gets user-name matching `id` from db.
  Returns immediately, and calls `cb` with user-name
  when response is available."
  [id cb]
  (schedule-async (fn []
                    (Thread/sleep 2000)
                    (get db id))
                  cb)
  (println "fired query asynchronously :)"))


;; NORMAL CALLBACK
;; ===============
(defn async-cb []
  (get-user-from-db :user0
                    #(let [massaged-resp (seq %)]
                       (println "via cb:" massaged-resp)
                       (println "but can't access outside callback :(")))
  (println "async :)"))


;; CHANNELS
;; ========
(defn almost-async-ch []
  (let [ch (chan)]
    (get-user-from-db :user1
                      #(>! ch %))
    (println "async :)")
    (println "but blocks on accessing response :(")
    (let [resp (<! ch)
          massaged-resp (seq resp)]
      (println "via ch:" massaged-resp)
      massaged-resp)))


;; CHANNELS + GO BLOCK
;; ===================
(defn async-ch-go []
  (let [ch (chan)]
    (get-user-from-db :user2
                      #(>! ch %))
    (println "async :)")
    (go
      (let [resp (<! ch)
            massaged-resp (seq resp)]
        (println "via go ch:" massaged-resp)
        (println "and didn't block!")
        massaged-resp))))
