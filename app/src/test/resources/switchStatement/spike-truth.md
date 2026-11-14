# switchStatement: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["0"]:::LITERAL
    n2["100"]:::LITERAL
    n3["10"]:::LITERAL
    n4["200"]:::LITERAL
    n5["20"]:::LITERAL
    n6["300"]:::LITERAL
    n7["30"]:::LITERAL
    n8["3"]:::LITERAL
    n9["500"]:::LITERAL
    n10["chosen"]:::VARIABLE
    n11["chosen"]:::VARIABLE
    n12["chosen"]:::VARIABLE
    n13["chosen"]:::VARIABLE
    n14["chosen"]:::VARIABLE
    n15["out"]:::VARIABLE
    n16["selector"]:::VARIABLE
    n17["selector"]:::VARIABLE
    n18["side"]:::VARIABLE
    n19["side"]:::VARIABLE
    n20["side"]:::VARIABLE
  end
  n0 --> n15
  n1 --> n10
  n11 --> n0
  n13 --> n0
  n14 --> n0
  n16 -->|if| n11
  n16 -->|if| n12
  n16 -->|if| n13
  n16 -->|if| n14
  n16 -->|if| n19
  n16 -->|if| n20
  n18 --> n0
  n19 --> n0
  n2 --> n14
  n20 --> n0
  n3 --> n11
  n4 --> n18
  n5 --> n12
  n6 --> n19
  n7 --> n13
  n8 --> n17
  n9 --> n20
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
