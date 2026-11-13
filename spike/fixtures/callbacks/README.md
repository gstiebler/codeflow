Callback scenarios for the dataflow. Same style as `tiny`: own `@POST`/`@Path` annotations, no jars.
Each endpoint takes a `param` and tries to reach `ItemRepository.save` only through the callback
mechanism under test, so the pair `POST /cb/<name> -> ItemRepository.save` exists iff the flow is
followed. `spike/test/extract-callbacks.sh` builds it and prints the table below.

Baseline (default `DELOMBOK_MODE`, i.e. no-delombok; Joern 4.0.570; no Joern errors, including on the
method reference):

```
scenario                  | pair found?
--------------------------|------------
a captured variable       | FOUND
b lambda parameter        | GAP
c lambda return           | FOUND
d method reference        | GAP
e own callback interface  | GAP
f control (direct)        | FOUND
```

Where each GAP stops (last node the forward walk from the endpoint reaches):

- b: `of(param).forEach(<lambda>)` (CallbackApi.java:26); the value never enters the lambda's parameter `x`.
- d: `of(param).forEach(repo::save)` (CallbackApi.java:41); never reaches `save`.
- e: `this.runner.run(param, <lambda>)` (CallbackApi.java:48), plus `call(v)` in Runner.java:7 and
  `String v` in Callback.java:6; the interface call has no edge to the lambda body, so `v -> repo.save(v)` is never reached.
