# externalConstant: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["1"]:::LITERAL
    n2["MAX_RETRIES"]:::VARIABLE
    n3["budget"]:::VARIABLE
    n4["spent"]:::VARIABLE
  end
  n0 --> n4
  n1 --> n0
  n2 --> n3
  n3 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
