# parentMethod: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["Child"]:::EXTERNAL
    n1["30"]:::LITERAL
    n2["child"]:::OBJ_VARIABLE
    n3["out"]:::VARIABLE
  end
  subgraph m1["adjust"]
    n4["scale"]:::EXTERNAL
    n5["input"]:::FUNC_PARAM
    n6["5"]:::LITERAL
    n7["adjust"]:::RETURN
    n8["scaled"]:::VARIABLE
    n9["shifted"]:::VARIABLE
    n10["super"]:::OBJ_VARIABLE
  end
  subgraph m2["scale"]
    n11["*"]:::BIN_OP
    n12["factor"]:::FUNC_PARAM
    n13["3"]:::LITERAL
    n14["scale"]:::RETURN
  end
  subgraph m3["shift"]
    n15["+"]:::BIN_OP
    n16["amount"]:::FUNC_PARAM
    n17["shift"]:::RETURN
  end
  n18["offset"]:::VARIABLE
  n0 --> n2
  n1 --> n5
  n10 --> n18
  n10 --> n4
  n11 --> n14
  n12 --> n11
  n13 --> n11
  n15 --> n17
  n16 --> n15
  n17 --> n9
  n18 --> n10
  n18 --> n15
  n2 --> n10
  n2 --> n18
  n4 --> n8
  n5 --> n16
  n6 --> n18
  n7 --> n3
  n8 --> n7
  n9 --> n4
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
