(ns functional-core-async.examples
  (:require [functional-core-async.core :refer :all]))

;; SETUP
;; =====
;; remote db
(def db
  {:user0 "callback"
   :user1 "channel + go"})

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
                    #(let [resp %
                           massaged-resp (seq resp)]
                       (println "via cb:" massaged-resp)
                       (println "but can't access outside callback :(")))
  (println "async :)"))


;; CHANNELS
;; ========
(defn async-ch []
  (let [ch (chan)]
    (get-user-from-db :user1 #(>!! ch %))
    (println "async :)")
    (println "but blocks on read :(")
    (let [resp (<!! ch)
          massaged-resp (seq resp)]
      (println "via ch:" massaged-resp)
      (println "but can access outside :)")
      massaged-resp)))


;; CHANNELS + GO
;; =============
(defn async-ch-go []
  (let [ch (chan)]
    (get-user-from-db :user1 #(>!! ch %))
    (println "async :)")
    (go
      (<! ch
          #(let [resp %
                 massaged-resp (seq resp)]
             (println "via ch/go:" massaged-resp)
             (println "and can access outside :)")
             massaged-resp)))))


;; HOT DOG MACHINE
;; ===============
(defn hot-dog-machine
  [in out hc]
  (let [recurse #(go (hot-dog-machine in out %))]
    (when (> hc 0)
      (go<! [input in]
        (if (= 3 input)
          (go>! [out "hot dog"]
            (recurse (dec hc)))
          (go>! [out "wilted lettuce"]
            (recurse hc)))))))

#_(let [in (chan)
      out (chan)
      _ (hot-dog-machine in out 2)]
  (>!! in "pocket lint")
  (println (<!! out))

  (>!! in 3)
  (println (<!! out))

  (>!! in 3)
  (println (<!! out)))
