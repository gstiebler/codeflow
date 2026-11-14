# switchStatement: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["0"]:::LITERAL
    n2["100"]:::LITERAL
    n3["10"]:::LITERAL
    n4["1"]:::LITERAL
    n5["200"]:::LITERAL
    n6["20"]:::LITERAL
    n7["2"]:::LITERAL
    n8["300"]:::LITERAL
    n9["30"]:::LITERAL
    n10["3"]:::LITERAL
    n11["3"]:::LITERAL
    n12["500"]:::LITERAL
    n13["chosen"]:::VARIABLE
    n14["out"]:::VARIABLE
    n15["selector"]:::VARIABLE
    n16["side"]:::VARIABLE
  end
  n0 --> n14
  n1 --> n13
  n10 --> n15
  n12 --> n16
  n13 --> n0
  n15 -->|if| n11
  n15 -->|if| n12
  n15 -->|if| n13
  n15 -->|if| n16
  n15 -->|if| n2
  n15 -->|if| n3
  n15 -->|if| n4
  n15 -->|if| n6
  n15 -->|if| n7
  n15 -->|if| n8
  n15 -->|if| n9
  n16 --> n0
  n2 --> n13
  n3 --> n13
  n5 --> n16
  n6 --> n13
  n8 --> n16
  n9 --> n13
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
