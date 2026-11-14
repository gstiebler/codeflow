# unboundLocal: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["&&"]:::BIN_OP
    n1["cast"]:::BIN_OP
    n2["instanceOf"]:::BIN_OP
    n3["println"]:::EXTERNAL
    n4["println"]:::EXTERNAL
    n5["args"]:::FUNC_PARAM
    n6["#quot;none#quot;"]:::LITERAL
    n7["true"]:::LITERAL
    n8["System"]:::OBJ_VARIABLE
    n9["text"]:::OBJ_VARIABLE
    n10["value"]:::OBJ_VARIABLE
  end
  n11["out"]:::VARIABLE
  n0 -->|if| n11
  n0 -->|if| n3
  n0 -->|if| n8
  n0 -->|if| n9
  n1 --> n9
  n10 --> n1
  n10 --> n2
  n10 -->|if| n0
  n10 -->|if| n2
  n11 --> n3
  n11 --> n4
  n11 --> n8
  n2 --> n0
  n2 -->|if| n1
  n2 -->|if| n10
  n2 -->|if| n7
  n2 -->|if| n9
  n5 --> n10
  n6 --> n4
  n8 --> n11
  n9 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
