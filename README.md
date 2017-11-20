# functional-core-async

A *tiny*, *simple*  implementation of the meat of core.async.

'functional' as in 'it works!' 😀

## Why

[core.async](https://github.com/clojure/core.async) is a great tool.

It makes writing concurrent software much simpler by getting data out
of callbacks through the use of magic portals called `channels`. This
is a minimal implementation using an event loop and functions.

**NOTE:** This is an experimental project through which I want to explore
how machinery like `core.async` can be implemented. Read more
[here](https://groups.google.com/forum/#!topic/clojure/1wmblSTtw2w).

## Differences from `core.async`
- `>!` and `<!` are implemented as functions and play nicely with the rest of Clojure
- `>!!` and `<!!` don't exist - the single bang versions work outside `go` blocks too
- `go` blocks (lightweight 'threads') are multiplexed over a single JVM thread, but are
promoted to real JVM threads if they don't complete within 10ms
- `thread` blocks don't exist because `go` blocks are autopromoted
- there are cases where `core.async` can run two `go` blocks on the same thread, while
this implementation will have to move one of them to another thread ([more](https://github.com/divs1210/functional-core-async/issues/1))

## Usage

Let's look at an everyday async call to the database to fetch a string
corresponding to the given id -

### Simple Callback
```clojure
;; from examples.clj
(defn async-callback []
  (get-user-from-db :user0
                    #(let [massaged-resp (seq %)]
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
                      #(>! ch %))
    (println "but blocks on accessing response :(")
    (let [resp (<! ch)
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
(defn async-ch+go []
  (let [ch (chan)]
    (get-user-from-db :user2
                      #(>! ch %))
    (go
      (let [resp (<! ch)
            massaged-resp (seq resp)]
        (println "via go ch:" massaged-resp)
        (println "and didn't block!")
        massaged-resp))))
```

This version is only slightly different to the previous one.
We put the fn body after the async call to the database inside
a `go` block, which is executed on the `async-executor` thread,
immediately returning a channel.

We can then call `(<! c)` on that channel to get `massaged-resp`.
So now we have sequential code instead of nested hell while
being fully async!

### Polling
`alts!` can be used to listen on a bunch of channels, and do
something when you get a value from one of them.

```clojure
(alts!
  {(chan)         #(println :chan %)
   (timeout 1000) #(println :timeout %)})
```
This will print ":timeout nil" after 1s.

**NOTE:** The `timeout` function returns a channel that closes after the given time
in milliseconds.

## The Hot Dog Machine Process You’ve Been Longing For

Here's a port of the [Hot Dog Machine](https://www.braveclojure.com/core-async/)

```clojure
(defn hot-dog-machine-v2
  [hot-dog-count]
  (let [in (chan)
        out (chan)]
    (go (loop [hc hot-dog-count]
          (if (> hc 0)
            (let [input (<! in)]
             (if (= 3 input)
                (do (>! out "hot dog")
                    (recur (dec hc)))
                (do (>! out "wilted lettuce")
                    (recur hc))))
           (do (close! in)
               (close! out)))))
    [in out]))
```

Let's give it a try:
```clojure
(let [[in out] (hot-dog-machine-v2 2)]
  (>! in "pocket lint")
  (println (<! out))

  (>! in 3)
  (println (<! out))

  (>! in 3)
  (println (<! out))

  (>! in 3)
  (<! out))
; => wilted lettuce
; => hotdog
; => hotdog
; => nil
```

## TODO

* preserve thread-local bindings in `go` blocks
* update `alts!` to put with puts

## License

Copyright © 2017 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0
