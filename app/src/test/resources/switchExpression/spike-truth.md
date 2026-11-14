# switchExpression: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["*"]:::BIN_OP
    n1["+"]:::BIN_OP
    n2["-"]:::BIN_OP
    n3["sizeOf"]:::BIN_OP
    n4["#lt;init#gt;"]:::EXTERNAL
    n5["ANY"]:::EXTERNAL
    n6["args"]:::FUNC_PARAM
    n7["#quot;unsupported#quot;"]:::LITERAL
    n8["0"]:::LITERAL
    n9["11"]:::LITERAL
    n10["13"]:::LITERAL
    n11["2"]:::LITERAL
    n12["41"]:::LITERAL
    n13["7"]:::LITERAL
    n14["$obj0"]:::OBJ_VARIABLE
    n15["base"]:::VARIABLE
    n16["bonus"]:::VARIABLE
    n17["bonus"]:::VARIABLE
    n18["doubled"]:::VARIABLE
    n19["fallback"]:::VARIABLE
    n20["fallback"]:::VARIABLE
    n21["kind"]:::VARIABLE
    n22["kind"]:::VARIABLE
    n23["note"]:::VARIABLE
    n24["note"]:::VARIABLE
    n25["note"]:::VARIABLE
    n26["out"]:::VARIABLE
    n27["total"]:::VARIABLE
  end
  n0 --> n18
  n1 --> n26
  n10 --> n20
  n11 --> n0
  n12 --> n25
  n13 --> n15
  n14 --> n4
  n16 --> n0
  n19 --> n2
  n21 -->|if| n14
  n23 --> n1
  n23 --> n2
  n24 --> n1
  n27 --> n1
  n3 --> n22
  n5 --> n14
  n6 --> n3
  n7 --> n4
  n8 --> n24
  n9 --> n17
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
