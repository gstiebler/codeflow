# if2: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["classify"]
    n0["#lt;"]:::BIN_OP
    n1["#gt;"]:::BIN_OP
    n2["#gt;"]:::BIN_OP
    n3["limit"]:::FUNC_PARAM
    n4["v"]:::FUNC_PARAM
    n5["if"]:::BIN_OP
    n6["0"]:::LITERAL
    n7["0"]:::LITERAL
    n8["100"]:::LITERAL
    n9["1"]:::LITERAL
    n10["2"]:::LITERAL
    n11["3"]:::LITERAL
    n12["4"]:::LITERAL
    n13["classify"]:::RETURN
    n14["r"]:::VARIABLE
    n15["r"]:::VARIABLE
    n16["r"]:::VARIABLE
    n17["r"]:::VARIABLE
    n18["r"]:::VARIABLE
    n19["s"]:::VARIABLE
  end
  subgraph m1["either"]
    n20["#gt;"]:::BIN_OP
    n21["v"]:::FUNC_PARAM
    n22["0"]:::LITERAL
    n23["6"]:::LITERAL
    n24["7"]:::LITERAL
    n25["either"]:::RETURN
  end
  subgraph m2["main"]
    n26["10"]:::LITERAL
    n27["3"]:::LITERAL
    n28["a"]:::VARIABLE
    n29["b"]:::VARIABLE
    n30["c"]:::VARIABLE
    n31["d"]:::VARIABLE
  end
  n0 -->|if| n5
  n1 -->|if| n13
  n10 --> n16
  n11 --> n17
  n12 --> n18
  n13 --> n29
  n15 --> n13
  n16 --> n5
  n17 --> n5
  n18 --> n5
  n19 --> n13
  n2 -->|if| n13
  n2 -->|if| n5
  n20 -->|if| n25
  n21 --> n20
  n22 --> n20
  n23 --> n25
  n24 --> n25
  n25 --> n31
  n26 --> n3
  n27 --> n28
  n28 --> n21
  n28 --> n4
  n29 --> n30
  n3 --> n1
  n4 --> n0
  n4 --> n1
  n4 --> n2
  n5 --> n19
  n6 --> n14
  n7 --> n0
  n8 --> n2
  n9 --> n15
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
