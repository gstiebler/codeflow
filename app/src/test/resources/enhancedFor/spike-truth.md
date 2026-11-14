# enhancedFor: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["hasNext"]:::EXTERNAL
    n2["iterator"]:::EXTERNAL
    n3["length"]:::EXTERNAL
    n4["next"]:::EXTERNAL
    n5["of"]:::EXTERNAL
    n6["#quot;a#quot;"]:::LITERAL
    n7["0"]:::LITERAL
    n8["$iterLocal0"]:::OBJ_VARIABLE
    n9["names"]:::OBJ_VARIABLE
    n10["name"]:::OBJ_VARIABLE
    n11["size"]:::VARIABLE
    n12["total"]:::VARIABLE
    n13["total"]:::VARIABLE
  end
  n0 --> n12
  n1 -->|if| n10
  n1 -->|if| n11
  n1 -->|if| n12
  n10 --> n3
  n11 --> n0
  n12 --> n0
  n13 --> n0
  n2 --> n8
  n3 --> n11
  n4 --> n10
  n5 --> n9
  n6 --> n5
  n7 --> n13
  n8 --> n1
  n8 --> n4
  n9 --> n2
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
