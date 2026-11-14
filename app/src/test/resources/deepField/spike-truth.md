# deepField: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["Gauge"]:::EXTERNAL
    n1["17"]:::LITERAL
    n2["gauge"]:::OBJ_VARIABLE
    n3["taken"]:::VARIABLE
  end
  subgraph m1["record"]
    n4["18"]:::LITERAL
  end
  n5["reading"]:::VARIABLE
  n0 --> n2
  n1 --> n5
  n2 --> n5
  n4 --> n5
  n5 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
