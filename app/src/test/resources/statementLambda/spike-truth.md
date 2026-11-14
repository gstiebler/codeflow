# statementLambda: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["#lt;lambda#gt;0"]
    n0["+"]:::BIN_OP
    n1["value"]:::FUNC_PARAM
    n2["#lt;lambda#gt;0"]:::RETURN
  end
  subgraph m1["main"]
    n3["applyAsInt"]:::EXTERNAL
    n4["statementLambda.App.#lt;lambda#gt;0:#lt;unresolve"]:::EXTERNAL
    n5["4"]:::LITERAL
    n6["twice"]:::OBJ_VARIABLE
    n7["out"]:::VARIABLE
  end
  n0 --> n2
  n1 --> n0
  n3 --> n7
  n4 --> n6
  n5 --> n3
  n6 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
