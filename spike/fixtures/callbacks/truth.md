# Callbacks fixture: value graph per scenario

Source: `spike/fixtures/callbacks/graph.json`. Nodes are Joern nodes (`Class:line LABEL` + code), edges are codeflow value edges reachable from each endpoint, labelled `kind: carrier`. Red = db sink (`ItemRepository.save` call); red dashed = a sink in the scenario that the walk does NOT reach (the gap). Regenerate with `UPDATE_SNAPSHOTS=1 spike/test/extract-callbacks.sh`.

## a. lambda captures param (FOUND)

```mermaid
flowchart TD
  a0["CallbackApi:18:26 METHOD_PARAMETER_IN<br/>String param"]
  a1["CallbackApi:19:50 CALL<br/>this.repo"]
  a2["CallbackApi:19:50 CALL<br/>this.repo.save(param)"]
  style a2 fill:#fee2e2,stroke:#dc2626
  a3["CallbackApi:19:50 IDENTIFIER<br/>this"]
  a4["CallbackApi:19:50 RETURN<br/>return repo.save(param);"]
  a5["CallbackApi:19:60 IDENTIFIER<br/>param"]
  a6["CallbackApi:19:9 CALL<br/>empty().orElseGet(#lt;lambda#gt;)"]
  a7["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  a8["ItemRepository:5:5 METHOD_PARAMETER_IN<br/>this"]
  a0 -->|"local: param"| a3
  a0 -->|"local: param"| a5
  a1 -->|"arg: arg 0 this"| a8
  a1 -->|"local: field CallbackApi.repo"| a2
  a2 -->|"local: save()"| a4
  a3 -->|"local: this"| a1
  a4 -->|"ret: return of #lt;lambda#gt;0()"| a6
  a5 -->|"arg: arg 1 value"| a7
  a5 -->|"local: param"| a2
```

## b. value into lambda parameter (FOUND)

```mermaid
flowchart TD
  b0["CallbackApi:25:29 METHOD_PARAMETER_IN<br/>String param"]
  b1["CallbackApi:26:17 IDENTIFIER<br/>param"]
  b2["CallbackApi:26:32 METHOD_PARAMETER_IN<br/>ANY x"]
  b3["CallbackApi:26:37 CALL<br/>this.repo.save(x)"]
  style b3 fill:#fee2e2,stroke:#dc2626
  b4["CallbackApi:26:37 RETURN<br/>return repo.save(x);"]
  b5["CallbackApi:26:47 IDENTIFIER<br/>x"]
  b6["CallbackApi:26:9 CALL<br/>of(param)"]
  b7["CallbackApi:26:9 CALL<br/>of(param).forEach(#lt;lambda#gt;)"]
  b8["CallbackApi:26:9 METHOD_REF<br/>app.api.CallbackApi.#lt;lambda#gt;1:#lt;unresolvedSignature#gt;(1)"]
  b9["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  b0 -->|"local: param"| b1
  b1 -->|"local: param"| b6
  b2 -->|"local: x"| b5
  b3 -->|"local: save()"| b4
  b4 -->|"ret: return of #lt;lambda#gt;1()"| b7
  b5 -->|"arg: arg 1 value"| b9
  b5 -->|"local: x"| b3
  b6 -->|"arg: callback arg 1 x"| b2
  b6 -->|"arg: callback arg"| b8
  b6 -->|"local: of()"| b7
  b8 -->|"local: app.api.CallbackApi.#lt;lambda#gt;1:#lt;unresolvedSignature#gt;(1)"| b7
```

## c. value out of lambda return (FOUND)

```mermaid
flowchart TD
  c0["CallbackApi:32:30 METHOD_PARAMETER_IN<br/>String param"]
  c1["CallbackApi:33:16 CALL<br/>String s = Optional.of(param).map(v -#gt; v + #quot;!#quot;).orElse(#quot;#quot;)"]
  c2["CallbackApi:33:16 IDENTIFIER<br/>s"]
  c3["CallbackApi:33:20 CALL<br/>of(param)"]
  c4["CallbackApi:33:20 CALL<br/>of(param).map(#lt;lambda#gt;)"]
  c5["CallbackApi:33:20 CALL<br/>of(param).map(#lt;lambda#gt;).orElse(#quot;#quot;)"]
  c6["CallbackApi:33:20 METHOD_REF<br/>app.api.CallbackApi.#lt;lambda#gt;2:#lt;unresolvedSignature#gt;(1)"]
  c7["CallbackApi:33:32 IDENTIFIER<br/>param"]
  c8["CallbackApi:33:43 METHOD_PARAMETER_IN<br/>ANY v"]
  c9["CallbackApi:33:48 CALL<br/>v + #quot;!#quot;"]
  c10["CallbackApi:33:48 IDENTIFIER<br/>v"]
  c11["CallbackApi:33:48 RETURN<br/>return v + #quot;!#quot;;"]
  c12["CallbackApi:34:19 IDENTIFIER<br/>s"]
  c13["CallbackApi:34:9 CALL<br/>this.repo.save(s)"]
  style c13 fill:#fee2e2,stroke:#dc2626
  c14["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  c0 -->|"local: param"| c7
  c1 -->|"local: ="| c2
  c10 -->|"local: v"| c9
  c11 -->|"ret: return of #lt;lambda#gt;2()"| c4
  c12 -->|"arg: arg 1 value"| c14
  c12 -->|"local: s"| c13
  c2 -->|"local: s"| c12
  c3 -->|"arg: callback arg 1 v"| c8
  c3 -->|"arg: callback arg"| c6
  c3 -->|"local: of()"| c4
  c4 -->|"local: map()"| c5
  c5 -->|"local: orElse()"| c1
  c6 -->|"local: app.api.CallbackApi.#lt;lambda#gt;2:#lt;unresolvedSignature#gt;(1)"| c4
  c7 -->|"local: param"| c3
  c8 -->|"local: v"| c10
  c9 -->|"local: v + #quot;!#quot;"| c11
```

## d. method reference repo::save (FOUND)

```mermaid
flowchart TD
  d0["CallbackApi:40:27 METHOD_PARAMETER_IN<br/>String param"]
  d1["CallbackApi:41:17 IDENTIFIER<br/>param"]
  d2["CallbackApi:41:32 METHOD_REF<br/>repo::save"]
  style d2 fill:#fee2e2,stroke:#dc2626
  d3["CallbackApi:41:9 CALL<br/>of(param)"]
  d4["CallbackApi:41:9 CALL<br/>of(param).forEach(repo::save)"]
  d5["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  d0 -->|"local: param"| d1
  d1 -->|"local: param"| d3
  d2 -->|"local: repo::save"| d4
  d3 -->|"arg: callback arg 1 value"| d5
  d3 -->|"arg: callback arg"| d2
  d3 -->|"local: of()"| d4
```

## e. own Callback interface (FOUND)

```mermaid
flowchart TD
  e0["Callback:6:15 METHOD_PARAMETER_IN<br/>String v"]
  e1["CallbackApi:47:29 METHOD_PARAMETER_IN<br/>String param"]
  e2["CallbackApi:48:20 IDENTIFIER<br/>param"]
  e3["CallbackApi:48:27 METHOD_PARAMETER_IN<br/>ANY v"]
  e4["CallbackApi:48:32 CALL<br/>this.repo.save(v)"]
  style e4 fill:#fee2e2,stroke:#dc2626
  e5["CallbackApi:48:32 RETURN<br/>return repo.save(v);"]
  e6["CallbackApi:48:42 IDENTIFIER<br/>v"]
  e7["CallbackApi:48:9 CALL<br/>this.runner.run(param, #lt;lambda#gt;)"]
  e8["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  e9["Runner:6:21 METHOD_PARAMETER_IN<br/>String v"]
  e10["Runner:7:17 IDENTIFIER<br/>v"]
  e11["Runner:7:9 CALL<br/>call(v)"]
  e1 -->|"local: param"| e2
  e10 -->|"arg: arg 1 v"| e0
  e10 -->|"arg: callback arg 1 v"| e3
  e10 -->|"local: v"| e11
  e2 -->|"arg: arg 1 v"| e9
  e2 -->|"local: param"| e7
  e3 -->|"local: v"| e6
  e4 -->|"local: save()"| e5
  e5 -->|"ret: return of #lt;lambda#gt;3()"| e11
  e6 -->|"arg: arg 1 value"| e8
  e6 -->|"local: v"| e4
  e9 -->|"local: v"| e10
```

## f. direct repo.save (control) (FOUND)

```mermaid
flowchart TD
  f0["CallbackApi:54:24 METHOD_PARAMETER_IN<br/>String param"]
  f1["CallbackApi:55:19 IDENTIFIER<br/>param"]
  f2["CallbackApi:55:9 CALL<br/>this.repo.save(param)"]
  style f2 fill:#fee2e2,stroke:#dc2626
  f3["ItemRepository:5:17 METHOD_PARAMETER_IN<br/>String value"]
  f0 -->|"local: param"| f1
  f1 -->|"arg: arg 1 value"| f3
  f1 -->|"local: param"| f2
```
