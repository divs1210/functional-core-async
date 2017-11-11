# functional-core-async

A *tiny*, *simple*, *functional* implementation of CSP in Clojure.

## Why

[core.async](https://github.com/clojure/core.async) is a great tool.

It makes writing concurrent software much simpler by getting data out
of callbacks through the use of magic portals called `channels`.

The problem is that it is implemented using complex macros that are hard
to understand and port to other systems. They also pose problems such as
inability to work across fn-boundaries and non-composability.

This is a minimal implementation using an event loop and functions,
showcasing how something like `core.async` could work behind the scenes.

## Differences from `core.async`
- implemented as functions, not macros
- `(>! c 1)` channels are unbounded, ie. infinite buffer
- `(<! c)` blocks when channel is empty
- `>!!` and `<!!` don't exist - the single bang versions work outside `go` blocks too

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

## License

Copyright © 2017 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0
