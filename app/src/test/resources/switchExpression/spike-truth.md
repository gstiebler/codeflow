# switchExpression: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["*"]:::BIN_OP
    n1["+"]:::BIN_OP
    n2["-"]:::BIN_OP
    n3["sizeOf"]:::BIN_OP
    n4["throw"]:::BIN_OP
    n5["#lt;init#gt;"]:::EXTERNAL
    n6["ANY"]:::EXTERNAL
    n7["args"]:::FUNC_PARAM
    n8["#quot;unsupported#quot;"]:::LITERAL
    n9["0"]:::LITERAL
    n10["11"]:::LITERAL
    n11["13"]:::LITERAL
    n12["1"]:::LITERAL
    n13["2"]:::LITERAL
    n14["2"]:::LITERAL
    n15["3"]:::LITERAL
    n16["41"]:::LITERAL
    n17["7"]:::LITERAL
    n18["$obj0"]:::OBJ_VARIABLE
    n19["base"]:::VARIABLE
    n20["bonus"]:::VARIABLE
    n21["doubled"]:::VARIABLE
    n22["fallback"]:::VARIABLE
    n23["kind"]:::VARIABLE
    n24["note"]:::VARIABLE
    n25["out"]:::VARIABLE
    n26["total"]:::VARIABLE
  end
  n0 --> n21
  n1 --> n25
  n10 --> n20
  n11 --> n22
  n14 --> n0
  n16 --> n24
  n17 --> n19
  n18 --> n5
  n20 --> n0
  n22 --> n2
  n23 -->|if| n12
  n23 -->|if| n13
  n23 -->|if| n15
  n23 -->|if| n18
  n23 -->|if| n4
  n23 -->|if| n5
  n23 -->|if| n6
  n23 -->|if| n8
  n24 --> n1
  n24 --> n2
  n26 --> n1
  n3 --> n23
  n6 --> n18
  n7 --> n3
  n8 --> n5
  n9 --> n24
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
