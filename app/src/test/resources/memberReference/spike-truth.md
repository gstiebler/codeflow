# memberReference: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["get"]:::EXTERNAL
    n1["names::size"]:::EXTERNAL
    n2["of"]:::EXTERNAL
    n3["#quot;a#quot;"]:::LITERAL
    n4["counter"]:::OBJ_VARIABLE
    n5["names"]:::OBJ_VARIABLE
    n6["out"]:::VARIABLE
  end
  n0 --> n6
  n1 --> n4
  n2 --> n5
  n3 --> n2
  n4 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
