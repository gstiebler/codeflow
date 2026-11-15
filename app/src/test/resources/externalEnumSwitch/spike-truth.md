# externalEnumSwitch: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["rounding"]:::EXTERNAL
    n2["rounding"]:::EXTERNAL
    n3["if"]:::BIN_OP
    n4["0"]:::LITERAL
    n5["1"]:::LITERAL
    n6["2"]:::LITERAL
    n7["Config"]:::OBJ_VARIABLE
    n8["fromExpression"]:::VARIABLE
    n9["fromStatement"]:::VARIABLE
    n10["fromStatement"]:::VARIABLE
    n11["fromStatement"]:::VARIABLE
    n12["out"]:::VARIABLE
  end
  n0 --> n12
  n1 -->|if| n3
  n10 --> n3
  n11 --> n3
  n3 --> n0
  n4 --> n9
  n5 --> n10
  n6 --> n11
  n7 --> n1
  n7 --> n2
  n8 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
