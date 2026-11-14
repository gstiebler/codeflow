# Callbacks fixture: value graph per scenario

Source: `spike/fixtures/callbacks/graph.json`. Nodes are Joern nodes (`Class:line LABEL` + code), edges are codeflow value edges reachable from each endpoint, labelled `kind: carrier`. Red = db sink (`ItemRepository.save` call); red dashed = a sink in the scenario that the walk does NOT reach (the gap). Regenerate with `UPDATE_SNAPSHOTS=1 spike/test/extract-callbacks.sh`.

## a. lambda captures param (FOUND)

```mermaid
flowchart TD
  a0["CallbackApi:18 METHOD_PARAMETER_IN<br/>String param"]
  a1["CallbackApi:19 CALL<br/>empty().orElseGet(#lt;lambda#gt;)"]
  a2["CallbackApi:19 CALL<br/>this.repo"]
  a3["CallbackApi:19 CALL<br/>this.repo.save(param)"]
  style a3 fill:#fee2e2,stroke:#dc2626
  a4["CallbackApi:19 IDENTIFIER<br/>param"]
  a5["CallbackApi:19 IDENTIFIER<br/>this"]
  a6["CallbackApi:19 RETURN<br/>return repo.save(param);"]
  a7["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  a8["ItemRepository:5 METHOD_PARAMETER_IN<br/>this"]
  a0 -->|"local: param"| a4
  a0 -->|"local: param"| a5
  a2 -->|"arg: arg 0 this"| a8
  a2 -->|"local: field CallbackApi.repo"| a3
  a3 -->|"local: save()"| a6
  a4 -->|"arg: arg 1 value"| a7
  a4 -->|"local: param"| a3
  a5 -->|"local: this"| a2
  a6 -->|"ret: return of #lt;lambda#gt;0()"| a1
```

## b. value into lambda parameter (FOUND)

```mermaid
flowchart TD
  b0["CallbackApi:25 METHOD_PARAMETER_IN<br/>String param"]
  b1["CallbackApi:26 CALL<br/>of(param)"]
  b2["CallbackApi:26 CALL<br/>of(param).forEach(#lt;lambda#gt;)"]
  b3["CallbackApi:26 CALL<br/>this.repo.save(x)"]
  style b3 fill:#fee2e2,stroke:#dc2626
  b4["CallbackApi:26 IDENTIFIER<br/>param"]
  b5["CallbackApi:26 IDENTIFIER<br/>x"]
  b6["CallbackApi:26 METHOD_PARAMETER_IN<br/>ANY x"]
  b7["CallbackApi:26 METHOD_REF<br/>app.api.CallbackApi.#lt;lambda#gt;1:#lt;unresolvedSignature#gt;(1)"]
  b8["CallbackApi:26 RETURN<br/>return repo.save(x);"]
  b9["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  b0 -->|"local: param"| b4
  b1 -->|"arg: callback arg 1 x"| b6
  b1 -->|"arg: callback arg"| b7
  b1 -->|"local: of()"| b2
  b3 -->|"local: save()"| b8
  b4 -->|"local: param"| b1
  b5 -->|"arg: arg 1 value"| b9
  b5 -->|"local: x"| b3
  b6 -->|"local: x"| b5
  b7 -->|"local: app.api.CallbackApi.#lt;lambda#gt;1:#lt;unresolvedSignature#gt;(1)"| b2
  b8 -->|"ret: return of #lt;lambda#gt;1()"| b2
```

## c. value out of lambda return (FOUND)

```mermaid
flowchart TD
  c0["CallbackApi:32 METHOD_PARAMETER_IN<br/>String param"]
  c1["CallbackApi:33 CALL<br/>String s = Optional.of(param).map(v -#gt; v + #quot;!#quot;).orElse(#quot;#quot;)"]
  c2["CallbackApi:33 CALL<br/>of(param)"]
  c3["CallbackApi:33 CALL<br/>of(param).map(#lt;lambda#gt;)"]
  c4["CallbackApi:33 CALL<br/>of(param).map(#lt;lambda#gt;).orElse(#quot;#quot;)"]
  c5["CallbackApi:33 CALL<br/>v + #quot;!#quot;"]
  c6["CallbackApi:33 IDENTIFIER<br/>param"]
  c7["CallbackApi:33 IDENTIFIER<br/>s"]
  c8["CallbackApi:33 IDENTIFIER<br/>v"]
  c9["CallbackApi:33 METHOD_PARAMETER_IN<br/>ANY v"]
  c10["CallbackApi:33 METHOD_REF<br/>app.api.CallbackApi.#lt;lambda#gt;2:#lt;unresolvedSignature#gt;(1)"]
  c11["CallbackApi:33 RETURN<br/>return v + #quot;!#quot;;"]
  c12["CallbackApi:34 CALL<br/>this.repo.save(s)"]
  style c12 fill:#fee2e2,stroke:#dc2626
  c13["CallbackApi:34 IDENTIFIER<br/>s"]
  c14["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  c0 -->|"local: param"| c6
  c1 -->|"local: ="| c7
  c10 -->|"local: app.api.CallbackApi.#lt;lambda#gt;2:#lt;unresolvedSignature#gt;(1)"| c3
  c11 -->|"ret: return of #lt;lambda#gt;2()"| c3
  c13 -->|"arg: arg 1 value"| c14
  c13 -->|"local: s"| c12
  c2 -->|"arg: callback arg 1 v"| c9
  c2 -->|"arg: callback arg"| c10
  c2 -->|"local: of()"| c3
  c3 -->|"local: map()"| c4
  c4 -->|"local: orElse()"| c1
  c5 -->|"local: v + #quot;!#quot;"| c11
  c6 -->|"local: param"| c2
  c7 -->|"local: s"| c13
  c8 -->|"local: v"| c5
  c9 -->|"local: v"| c8
```

## d. method reference repo::save (FOUND)

```mermaid
flowchart TD
  d0["CallbackApi:40 METHOD_PARAMETER_IN<br/>String param"]
  d1["CallbackApi:41 CALL<br/>of(param)"]
  d2["CallbackApi:41 CALL<br/>of(param).forEach(repo::save)"]
  d3["CallbackApi:41 IDENTIFIER<br/>param"]
  d4["CallbackApi:41 METHOD_REF<br/>repo::save"]
  style d4 fill:#fee2e2,stroke:#dc2626
  d5["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  d0 -->|"local: param"| d3
  d1 -->|"arg: callback arg 1 value"| d5
  d1 -->|"arg: callback arg"| d4
  d1 -->|"local: of()"| d2
  d3 -->|"local: param"| d1
  d4 -->|"local: repo::save"| d2
```

## e. own Callback interface (FOUND)

```mermaid
flowchart TD
  e0["Callback:6 METHOD_PARAMETER_IN<br/>String v"]
  e1["CallbackApi:47 METHOD_PARAMETER_IN<br/>String param"]
  e2["CallbackApi:48 CALL<br/>this.repo.save(v)"]
  style e2 fill:#fee2e2,stroke:#dc2626
  e3["CallbackApi:48 CALL<br/>this.runner.run(param, #lt;lambda#gt;)"]
  e4["CallbackApi:48 IDENTIFIER<br/>param"]
  e5["CallbackApi:48 IDENTIFIER<br/>v"]
  e6["CallbackApi:48 METHOD_PARAMETER_IN<br/>ANY v"]
  e7["CallbackApi:48 RETURN<br/>return repo.save(v);"]
  e8["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  e9["Runner:6 METHOD_PARAMETER_IN<br/>String v"]
  e10["Runner:7 CALL<br/>call(v)"]
  e11["Runner:7 IDENTIFIER<br/>v"]
  e1 -->|"local: param"| e4
  e11 -->|"arg: arg 1 v"| e0
  e11 -->|"arg: callback arg 1 v"| e6
  e11 -->|"local: v"| e10
  e2 -->|"local: save()"| e7
  e4 -->|"arg: arg 1 v"| e9
  e4 -->|"local: param"| e3
  e5 -->|"arg: arg 1 value"| e8
  e5 -->|"local: v"| e2
  e6 -->|"local: v"| e5
  e7 -->|"ret: return of #lt;lambda#gt;3()"| e10
  e9 -->|"local: v"| e11
```

## f. direct repo.save (control) (FOUND)

```mermaid
flowchart TD
  f0["CallbackApi:54 METHOD_PARAMETER_IN<br/>String param"]
  f1["CallbackApi:55 CALL<br/>this.repo.save(param)"]
  style f1 fill:#fee2e2,stroke:#dc2626
  f2["CallbackApi:55 IDENTIFIER<br/>param"]
  f3["ItemRepository:5 METHOD_PARAMETER_IN<br/>String value"]
  f0 -->|"local: param"| f2
  f2 -->|"arg: arg 1 value"| f3
  f2 -->|"local: param"| f1
```
