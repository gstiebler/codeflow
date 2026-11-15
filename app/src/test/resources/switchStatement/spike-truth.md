# switchStatement: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["+"]:::BIN_OP
    n1["if"]:::BIN_OP
    n2["if"]:::BIN_OP
    n3["0"]:::LITERAL
    n4["100"]:::LITERAL
    n5["10"]:::LITERAL
    n6["200"]:::LITERAL
    n7["20"]:::LITERAL
    n8["300"]:::LITERAL
    n9["30"]:::LITERAL
    n10["3"]:::LITERAL
    n11["500"]:::LITERAL
    n12["chosen"]:::VARIABLE
    n13["chosen"]:::VARIABLE
    n14["chosen"]:::VARIABLE
    n15["chosen"]:::VARIABLE
    n16["chosen"]:::VARIABLE
    n17["out"]:::VARIABLE
    n18["selector"]:::VARIABLE
    n19["selector"]:::VARIABLE
    n20["side"]:::VARIABLE
    n21["side"]:::VARIABLE
    n22["side"]:::VARIABLE
  end
  n0 --> n17
  n1 --> n0
  n10 --> n19
  n11 --> n22
  n13 --> n2
  n15 --> n2
  n16 --> n2
  n18 -->|if| n1
  n18 -->|if| n2
  n2 --> n0
  n20 --> n1
  n21 --> n1
  n22 --> n1
  n3 --> n12
  n4 --> n16
  n5 --> n13
  n6 --> n20
  n7 --> n14
  n8 --> n21
  n9 --> n15
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
