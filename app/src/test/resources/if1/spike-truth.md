# if1: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["=="]:::BIN_OP
    n1["if"]:::BIN_OP
    n2["if"]:::BIN_OP
    n3["13"]:::LITERAL
    n4["17"]:::LITERAL
    n5["5"]:::LITERAL
    n6["7"]:::LITERAL
    n7["a"]:::VARIABLE
    n8["a"]:::VARIABLE
    n9["b"]:::VARIABLE
    n10["b"]:::VARIABLE
    n11["c"]:::VARIABLE
    n12["d"]:::VARIABLE
  end
  n0 -->|if| n1
  n0 -->|if| n2
  n1 --> n12
  n10 --> n0
  n10 --> n2
  n2 --> n11
  n3 --> n9
  n4 --> n7
  n5 --> n8
  n6 --> n0
  n7 --> n1
  n8 --> n1
  n8 --> n10
  n9 --> n2
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
