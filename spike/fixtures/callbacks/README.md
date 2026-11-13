Callback scenarios for the dataflow. Same style as `tiny`: own `@POST`/`@Path` annotations, no jars.
Each endpoint takes a `param` and tries to reach `ItemRepository.save` only through the callback
mechanism under test, so the pair `POST /cb/<name> -> ItemRepository.save` exists iff the flow is
followed. `spike/test/extract-callbacks.sh` builds it and prints the table below.

Default `DELOMBOK_MODE`, i.e. no-delombok; Joern 4.0.570; no Joern errors, including on the method
reference. `check` asserts every row is FOUND.

```
scenario                  | before | after
--------------------------|--------|------
a captured variable       | FOUND  | FOUND
b lambda parameter        | GAP    | FOUND
c lambda return           | FOUND  | FOUND
d method reference        | GAP    | FOUND
e own callback interface  | GAP    | FOUND
f control (direct)        | FOUND  | FOUND
```

What the dump shows (the design follows from it): a lambda is a method `<lambda>N` whose parameter 0
is the captured `this` (absent when it captures nothing); its METHOD_REF has no line and a
`typeFullName` equal to the lambda itself, not the interface. `repo::save` is a METHOD_REF typed
`ItemRepository`, with `methodFullName` lacking the `(n)` arity suffix. So the interface of a lambda
is read off the corpus parameter it is passed to (`Runner.run`'s `Callback cb`).

Where each GAP stopped before (last node the forward walk from the endpoint reaches):

- b: `of(param).forEach(<lambda>)` (CallbackApi.java:26); the value never enters the lambda's parameter `x`.
- d: `of(param).forEach(repo::save)` (CallbackApi.java:41); never reaches `save`.
- e: `this.runner.run(param, <lambda>)` (CallbackApi.java:48), plus `call(v)` in Runner.java:7 and
  `String v` in Callback.java:6; the interface call has no edge to the lambda body, so `v -> repo.save(v)` is never reached.
