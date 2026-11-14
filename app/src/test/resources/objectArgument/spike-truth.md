# objectArgument: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["Source"]:::EXTERNAL
    n1["7"]:::LITERAL
    n2["source"]:::OBJ_VARIABLE
    n3["out"]:::VARIABLE
  end
  subgraph m1["read"]
    n4["origin"]:::FUNC_PARAM
    n5["read"]:::RETURN
  end
  subgraph m2["#lt;init#gt;"]
    n6["seed"]:::FUNC_PARAM
  end
  n7["value"]:::VARIABLE
  n0 --> n2
  n1 --> n6
  n2 --> n4
  n2 --> n7
  n4 --> n7
  n5 --> n3
  n6 --> n7
  n7 --> n5
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
