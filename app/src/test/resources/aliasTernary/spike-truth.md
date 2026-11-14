# aliasTernary: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["#lt;init#gt;"]
    n0["amount"]:::FUNC_PARAM
  end
  subgraph m1["main"]
    n1["#gt;"]:::BIN_OP
    n2["sizeOf"]:::BIN_OP
    n3["ternary"]:::BIN_OP
    n4["App$Holder"]:::EXTERNAL
    n5["App$Holder"]:::EXTERNAL
    n6["args"]:::FUNC_PARAM
    n7["0"]:::LITERAL
    n8["11"]:::LITERAL
    n9["22"]:::LITERAL
    n10["$obj0"]:::OBJ_VARIABLE
    n11["$obj1"]:::OBJ_VARIABLE
    n12["chosen"]:::OBJ_VARIABLE
    n13["flag"]:::VARIABLE
    n14["total"]:::VARIABLE
  end
  n15["amount"]:::VARIABLE
  n0 --> n15
  n1 --> n13
  n10 --> n15
  n11 --> n15
  n12 --> n15
  n13 --> n3
  n13 -->|if| n10
  n13 -->|if| n11
  n15 --> n14
  n2 --> n1
  n3 --> n12
  n4 --> n10
  n5 --> n11
  n6 --> n2
  n7 --> n1
  n8 --> n0
  n9 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
