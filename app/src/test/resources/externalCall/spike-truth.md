# externalCall: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["abs"]:::EXTERNAL
    n2["println"]:::EXTERNAL
    n3["1"]:::LITERAL
    n4["5"]:::LITERAL
    n5["Math"]:::OBJ_VARIABLE
    n6["System"]:::OBJ_VARIABLE
    n7["a"]:::VARIABLE
    n8["b"]:::VARIABLE
    n9["c"]:::VARIABLE
  end
  n10["out"]:::VARIABLE
  n0 --> n9
  n1 --> n8
  n10 --> n2
  n3 --> n0
  n4 --> n7
  n5 --> n1
  n6 --> n10
  n7 --> n1
  n8 --> n0
  n8 --> n2
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
