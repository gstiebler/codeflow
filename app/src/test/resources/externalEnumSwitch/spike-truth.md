# externalEnumSwitch: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["rounding"]:::EXTERNAL
    n2["rounding"]:::EXTERNAL
    n3["0"]:::LITERAL
    n4["1"]:::LITERAL
    n5["2"]:::LITERAL
    n6["Config"]:::OBJ_VARIABLE
    n7["fromExpression"]:::VARIABLE
    n8["fromStatement"]:::VARIABLE
    n9["out"]:::VARIABLE
  end
  n0 --> n9
  n1 -->|if| n8
  n3 --> n8
  n4 --> n8
  n5 --> n8
  n6 --> n1
  n6 --> n2
  n7 --> n0
  n8 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
