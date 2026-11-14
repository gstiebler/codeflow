# arrayAccess: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["indexAccess"]:::BIN_OP
    n1["length"]:::EXTERNAL
    n2["args"]:::FUNC_PARAM
    n3["1"]:::LITERAL
    n4["picked"]:::OBJ_VARIABLE
    n5["position"]:::VARIABLE
    n6["size"]:::VARIABLE
  end
  n0 --> n4
  n1 --> n6
  n2 --> n0
  n3 --> n5
  n4 --> n1
  n5 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
