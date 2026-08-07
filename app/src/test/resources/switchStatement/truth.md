```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[3]:::LITERAL
    n4[selector]:::VARIABLE
    n5[0]:::LITERAL
    n6[chosen]:::VARIABLE
    n7[200]:::LITERAL
    n8[side]:::VARIABLE
    n9[1]:::LITERAL
    n10[==]:::BIN_OP
    n11[10]:::LITERAL
    n12[chosen]:::VARIABLE
    n13[2]:::LITERAL
    n14[==]:::BIN_OP
    n15[20]:::LITERAL
    n16[chosen]:::VARIABLE
    n17[300]:::LITERAL
    n18[side]:::VARIABLE
    n19[switch]:::VARIABLE
    n20[switch]:::VARIABLE
    n21[3]:::LITERAL
    n22[==]:::BIN_OP
    n23[30]:::LITERAL
    n24[chosen]:::VARIABLE
    n25[100]:::LITERAL
    n26[chosen]:::VARIABLE
    n27[500]:::LITERAL
    n28[side]:::VARIABLE
    n29[switch]:::VARIABLE
    n30[switch]:::VARIABLE
    n31[+]:::BIN_OP
    n32[out]:::VARIABLE
    n3[3]:::LITERAL --> n4[selector]:::VARIABLE
    n4[selector]:::VARIABLE --> n10[==]:::BIN_OP
    n4[selector]:::VARIABLE --> n14[==]:::BIN_OP
    n4[selector]:::VARIABLE -->|if| n19[switch]:::VARIABLE
    n4[selector]:::VARIABLE -->|if| n20[switch]:::VARIABLE
    n4[selector]:::VARIABLE --> n22[==]:::BIN_OP
    n4[selector]:::VARIABLE -->|if| n29[switch]:::VARIABLE
    n4[selector]:::VARIABLE -->|if| n30[switch]:::VARIABLE
    n5[0]:::LITERAL --> n6[chosen]:::VARIABLE
    n6[chosen]:::VARIABLE --> n19[switch]:::VARIABLE
    n7[200]:::LITERAL --> n8[side]:::VARIABLE
    n8[side]:::VARIABLE --> n20[switch]:::VARIABLE
    n8[side]:::VARIABLE --> n30[switch]:::VARIABLE
    n9[1]:::LITERAL --> n10[==]:::BIN_OP
    n11[10]:::LITERAL --> n12[chosen]:::VARIABLE
    n12[chosen]:::VARIABLE --> n29[switch]:::VARIABLE
    n13[2]:::LITERAL --> n14[==]:::BIN_OP
    n15[20]:::LITERAL --> n16[chosen]:::VARIABLE
    n16[chosen]:::VARIABLE --> n19[switch]:::VARIABLE
    n17[300]:::LITERAL --> n18[side]:::VARIABLE
    n18[side]:::VARIABLE --> n20[switch]:::VARIABLE
    n20[switch]:::VARIABLE --> n30[switch]:::VARIABLE
    n21[3]:::LITERAL --> n22[==]:::BIN_OP
    n23[30]:::LITERAL --> n24[chosen]:::VARIABLE
    n24[chosen]:::VARIABLE --> n29[switch]:::VARIABLE
    n25[100]:::LITERAL --> n26[chosen]:::VARIABLE
    n26[chosen]:::VARIABLE --> n29[switch]:::VARIABLE
    n27[500]:::LITERAL --> n28[side]:::VARIABLE
    n28[side]:::VARIABLE --> n30[switch]:::VARIABLE
    n29[switch]:::VARIABLE --> n31[+]:::BIN_OP
    n30[switch]:::VARIABLE --> n31[+]:::BIN_OP
    n31[+]:::BIN_OP --> n32[out]:::VARIABLE
  end
  linkStyle 3 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 4 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 6 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 7 stroke:#6a6a6a,color:#6a6a6a
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
