# operators: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["!="]:::BIN_OP
    n1["%"]:::BIN_OP
    n2["&&"]:::BIN_OP
    n3["*"]:::BIN_OP
    n4["+"]:::BIN_OP
    n5["-"]:::BIN_OP
    n6["#lt;="]:::BIN_OP
    n7["#lt;"]:::BIN_OP
    n8["=="]:::BIN_OP
    n9["#gt;="]:::BIN_OP
    n10["#gt;"]:::BIN_OP
    n11["div"]:::BIN_OP
    n12["||"]:::BIN_OP
    n13["10"]:::LITERAL
    n14["3"]:::LITERAL
    n15["a"]:::VARIABLE
    n16["b"]:::VARIABLE
    n17["both"]:::VARIABLE
    n18["difference"]:::VARIABLE
    n19["either"]:::VARIABLE
    n20["equal"]:::VARIABLE
    n21["greater"]:::VARIABLE
    n22["greaterOrEqual"]:::VARIABLE
    n23["less"]:::VARIABLE
    n24["lessOrEqual"]:::VARIABLE
    n25["notEqual"]:::VARIABLE
    n26["product"]:::VARIABLE
    n27["quotient"]:::VARIABLE
    n28["remainder"]:::VARIABLE
    n29["sum"]:::VARIABLE
  end
  n0 --> n25
  n1 --> n28
  n10 --> n21
  n11 --> n27
  n12 --> n19
  n13 --> n15
  n14 --> n16
  n15 --> n0
  n15 --> n1
  n15 --> n10
  n15 --> n11
  n15 --> n3
  n15 --> n4
  n15 --> n5
  n15 --> n6
  n15 --> n7
  n15 --> n8
  n15 --> n9
  n16 --> n0
  n16 --> n1
  n16 --> n10
  n16 --> n11
  n16 --> n3
  n16 --> n4
  n16 --> n5
  n16 --> n6
  n16 --> n7
  n16 --> n8
  n16 --> n9
  n2 --> n17
  n20 --> n12
  n20 --> n2
  n23 --> n12
  n23 --> n2
  n3 --> n26
  n4 --> n29
  n5 --> n18
  n6 --> n24
  n7 --> n23
  n8 --> n20
  n9 --> n22
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
