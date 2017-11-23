```
He had found a Nutri-Matic machine which had provided him with
a plastic cup filled with a liquid that was almost, but not quite,
entirely unlike tea.

The way it functioned was very interesting. When the Drink button
was pressed it made an instant but highly detailed examination of
the subject's taste buds, a spectroscopic analysis of the subject's
metabolism and then sent tiny experimental signals down the neural
pathways to the taste centers of the subject's brain to see what
was likely to go down well. However, no one knew quite why it did
this because it invariably delivered a cupful of liquid that was
almost, but not quite, entirely unlike tea.
```
*from The Hitchhiker's Guide to the Galaxy*

# functional-core-async

CSP - S = Communicating Processes = Green Threads!

## Why

- It makes writing concurrent software much simpler by getting data out of callbacks
through the use of magic portals called `channels`.
- It provides green threads via `go` blocks that can park and be multiplexed over JVM threads,
and communicate over channels.
- It can be ported to other systems and languages in a rather straightforward manner.
For example, here is [a javascript port](https://github.com/divs1210/coroutines.js).

## Differences from [`core.async`](https://github.com/clojure/core.async)
- CSP - S: making callbacky code look sequential requires access to compiler. This is avoided to aid portability.
- `>!` and `<!` are implemented as functions and take callbacks. These should be top-level in their `go` blocks.
- `go` blocks (green 'threads') are multiplexed over n Clojure `future`s, where n = number of cores.
- the `go` macro can only park a single `>!` or `<!` that is returned from its body.

## Usage

Let's look at an everyday async call to the database to fetch a string
corresponding to the given id -

### Simple Callback
```clojure
;; from examples.clj
(defn async-callback []
  (get-user-from-db :user0
                    #(let [resp %
                           massaged-resp (seq resp)]
                       (println "via cb:" massaged-resp)
                       (println "but can't access outside callback :("))))
```

The function fires a query to the db and immediately returns `nil`.

In this implementation, the response is locked inside the callback
and whatever code needs access to it should be put inside that callback.

This leads to what is called [callback-hell](http://callbackhell.com/),
which can be escaped with the help of those handy magic portals we talked about.

### Channels to The Rescue
```clojure
;; from examples.clj
(defn async-ch []
  (let [ch (chan)]
    (get-user-from-db :user1
                      #(>!! ch %))
    (println "but blocks on accessing response :(")
    (let [resp (<!! ch)
          massaged-resp (seq resp)]
      (println "via ch:" massaged-resp)
      massaged-resp)))
```

In this version, we have modified the callback to just put the response onto
the channel `ch`. The db call is made asynchronously and the call to print
is executed immediately afterwards.

When we get our response from the channel, however, the thread blocks, waiting
for the callback to complete and `ch` to receive a value.

We then take the return value from `ch` and voila! We have the response out of
the callback! It's unfortunate that our function has now become blocking, though.

### Fully Async
```clojure
;; from examples.clj
(defn async-ch-go []
  (let [ch (chan)]
    (get-user-from-db :user1
                      #(>!! ch %))
    (go
      (<! ch
          #(let [resp %
                 massaged-resp (seq resp)]
             (println "via ch/go:" massaged-resp)
             (println "and didn't block!")
             massaged-resp)))))
```
which can also be written as
```clojure
(defn async-ch-go []
  (let [ch (chan)]
    (get-user-from-db :user1
                      #(>!! ch %))
    (go<! [resp ch]
      (let [massaged-resp (seq resp)]
        (println "via ch/go:" massaged-resp)
        (println "and didn't block!")
        massaged-resp))))
```

This version is only slightly different to the previous one.
We put the fn body after the async call to the database inside
a `go` block, which is executed on the `async-executor` thread,
immediately returning a channel.

We can then call `(<!! c)` on that channel to get `massaged-resp`.
So now we have sequential code instead of nested hell while
being fully async!

## The Hot Dog Machine Process You’ve Been Longing For

Here's a port of the [Hot Dog Machine](https://www.braveclojure.com/core-async/)

```clojure
(defn hot-dog-machine
  [in out hot-dogs-left]
  (when (> hot-dogs-left 0)
    (go<! [input in]
      (if (= 3 input)
        (go>! [out "hot dog"]
          (hot-dog-machine in out (dec hot-dogs-left)))
        (go>! [out "wilted lettuce"]
          (hot-dog-machine in out hot-dogs-left))))))
```
Let's give it a try:
```clojure
(let [in (chan)
      out (chan)
      _ (hot-dog-machine in out 2)]
  (>!! in "pocket lint")
  (println (<!! out))

  (>!! in 3)
  (println (<!! out))

  (>!! in 3)
  (println (<!! out)))
; => wilted lettuce
; => hotdog
; => hotdog
```

## TODO

* preserve thread-local bindings in `go` blocks
* optimize scheduler: replace round-robin scheduling with per-channel queues and listeners
* `close!`
* `goproduce`/`goconsume`
* `alts!`

## License

Copyright © 2017 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0
