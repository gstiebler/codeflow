# forLoop: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["++"]:::BIN_OP
    n1["+="]:::BIN_OP
    n2["#lt;"]:::BIN_OP
    n3["if"]:::BIN_OP
    n4["if"]:::BIN_OP
    n5["0"]:::LITERAL
    n6["0"]:::LITERAL
    n7["1"]:::LITERAL
    n8["5"]:::LITERAL
    n9["7"]:::LITERAL
    n10["i"]:::VARIABLE
    n11["i"]:::VARIABLE
    n12["x"]:::VARIABLE
    n13["y"]:::VARIABLE
    n14["y"]:::VARIABLE
    n15["y"]:::VARIABLE
    n16["z"]:::VARIABLE
  end
  n10 --> n4
  n11 --> n0
  n11 --> n4
  n12 --> n2
  n13 --> n3
  n14 --> n3
  n15 --> n1
  n2 -->|if| n3
  n2 -->|if| n4
  n3 --> n15
  n3 --> n16
  n4 --> n11
  n4 --> n2
  n5 --> n13
  n6 --> n10
  n7 --> n1
  n8 --> n12
  n9 --> n14
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
