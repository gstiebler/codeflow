# if1: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["=="]:::BIN_OP
    n1["13"]:::LITERAL
    n2["17"]:::LITERAL
    n3["5"]:::LITERAL
    n4["7"]:::LITERAL
    n5["a"]:::VARIABLE
    n6["b"]:::VARIABLE
    n7["c"]:::VARIABLE
    n8["d"]:::VARIABLE
  end
  n0 -->|if| n5
  n0 -->|if| n6
  n1 --> n6
  n2 --> n5
  n3 --> n5
  n4 --> n0
  n5 --> n6
  n5 --> n8
  n6 --> n0
  n6 --> n7
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
