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
    n10["part"]:::VARIABLE
    n11["sum"]:::VARIABLE
  end
  subgraph m1["main"]
    n12["arrayInitializer"]:::BIN_OP
    n13["1"]:::LITERAL
    n14["2"]:::LITERAL
    n15["3"]:::LITERAL
    n16["out"]:::VARIABLE
  end
  n1 --> n11
  n10 --> n1
  n11 --> n1
  n11 --> n8
  n12 --> n6
  n13 --> n5
  n14 --> n12
  n15 --> n12
  n2 -->|if| n0
  n2 -->|if| n1
  n2 -->|if| n10
  n2 -->|if| n11
  n2 -->|if| n3
  n2 -->|if| n4
  n2 -->|if| n6
  n2 -->|if| n9
  n3 --> n10
  n4 --> n2
  n5 --> n11
  n6 --> n3
  n6 --> n4
  n7 --> n9
  n8 --> n16
  n9 --> n0
  n9 --> n2
  n9 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
