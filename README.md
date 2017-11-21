# functional-core-async

More [CPS](https://en.wikipedia.org/wiki/Continuation-passing_style) than [CSP](https://en.wikipedia.org/wiki/Communicating_sequential_processes).

## Why

[core.async](https://github.com/clojure/core.async) is a great tool.

It makes writing concurrent software much simpler by getting data out
of callbacks through the use of magic portals called `channels`. This
is a minimal implementation using an event loop and functions.

**NOTE:** This is an experimental project through which I want to explore
how machinery like `core.async` can be implemented. Read more
[here](https://groups.google.com/forum/#!topic/clojure/1wmblSTtw2w).

## Differences from `core.async`
- `>!` and `<!` are implemented as functions and take callbacks.
- `go` blocks (lightweight 'threads') are multiplexed over a single JVM thread. Each can have only one `<!` or `>!`.

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
  ([hotdog-count]
   (let [in (chan)
         out (chan)]
     (hot-dog-machine in out hotdog-count)
     [in out]))
  ([in out hc]
   (when (> hc 0)
     (go
       (<! in
           #(let [input %]
              (if (= 3 input)
                (go (>! out "hot dog"
                        (go (hot-dog-machine in out (dec hc)))))
                (go (>! out "wilted lettuce"
                        (go (hot-dog-machine in out hc)))))))))))
```

Let's give it a try:
```clojure
(let [[in out] (hot-dog-machine 2)]
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
* `alts!`
* `multi!` macro for nested `go` blocks

## License

Copyright © 2017 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0
