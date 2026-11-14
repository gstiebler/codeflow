# staticField: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["3"]:::LITERAL
    n1["5"]:::LITERAL
    n2["after"]:::VARIABLE
    n3["before"]:::VARIABLE
  end
  subgraph m1["bump"]
    n4["+"]:::BIN_OP
    n5["by"]:::FUNC_PARAM
  end
  n6["total"]:::VARIABLE
  n0 --> n5
  n1 --> n6
  n4 --> n6
  n5 --> n4
  n6 --> n2
  n6 --> n3
  n6 --> n4
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
