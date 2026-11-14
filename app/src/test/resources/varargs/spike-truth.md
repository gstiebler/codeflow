# varargs: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["total"]
    n0["++"]:::BIN_OP
    n1["+"]:::BIN_OP
    n2["#lt;"]:::BIN_OP
    n3["indexAccess"]:::BIN_OP
    n4["sizeOf"]:::BIN_OP
    n5["base"]:::FUNC_PARAM
    n6["rest"]:::FUNC_PARAM
    n7["0"]:::LITERAL
    n8["total"]:::RETURN
    n9["$idx0"]:::VARIABLE
    n10["$idx0"]:::VARIABLE
    n11["$idx0"]:::VARIABLE
    n12["part"]:::VARIABLE
    n13["sum"]:::VARIABLE
    n14["sum"]:::VARIABLE
  end
  subgraph m1["main"]
    n15["arrayInitializer"]:::BIN_OP
    n16["1"]:::LITERAL
    n17["2"]:::LITERAL
    n18["3"]:::LITERAL
    n19["out"]:::VARIABLE
  end
  n1 --> n14
  n10 --> n11
  n10 --> n2
  n11 --> n0
  n11 --> n2
  n12 --> n1
  n13 --> n1
  n13 --> n8
  n14 --> n1
  n14 --> n8
  n15 --> n6
  n16 --> n5
  n17 --> n15
  n18 --> n15
  n2 -->|if| n12
  n2 -->|if| n14
  n3 --> n12
  n4 --> n2
  n5 --> n13
  n6 --> n3
  n6 --> n4
  n7 --> n10
  n8 --> n19
  n9 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
