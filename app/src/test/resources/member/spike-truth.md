# member: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["func1"]
    n0["ClassX"]:::EXTERNAL
    n1["ClassY"]:::EXTERNAL
    n2["5"]:::LITERAL
    n3["6"]:::LITERAL
    n4["8"]:::LITERAL
    n5["x1"]:::OBJ_VARIABLE
    n6["y1"]:::OBJ_VARIABLE
    n7["y"]:::OBJ_VARIABLE
    n8["a"]:::VARIABLE
    n9["a"]:::VARIABLE
    n10["b"]:::VARIABLE
    n11["c"]:::VARIABLE
    n12["d"]:::VARIABLE
    n13["j"]:::VARIABLE
  end
  subgraph m1["main"]
    n14["App"]:::EXTERNAL
    n15["app"]:::OBJ_VARIABLE
  end
  subgraph m2["getMemberX"]
    n16["getMemberX"]:::RETURN
  end
  n17["memberA"]:::VARIABLE
  n18["memberX"]:::VARIABLE
  n19["x"]:::VARIABLE
  n0 --> n19
  n1 --> n7
  n11 --> n13
  n14 --> n15
  n15 --> n17
  n16 --> n11
  n17 --> n10
  n18 --> n12
  n18 --> n16
  n19 --> n18
  n19 --> n5
  n19 --> n6
  n2 --> n8
  n3 --> n9
  n4 --> n18
  n5 --> n18
  n6 --> n19
  n7 --> n19
  n7 --> n6
  n9 --> n17
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
