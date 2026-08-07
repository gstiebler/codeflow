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
    n19[3]:::LITERAL
    n20[==]:::BIN_OP
    n21[30]:::LITERAL
    n22[chosen]:::VARIABLE
    n23[100]:::LITERAL
    n24[chosen]:::VARIABLE
    n25[500]:::LITERAL
    n26[side]:::VARIABLE
    n27[+]:::BIN_OP
    n28[out]:::VARIABLE
    n3[3]:::LITERAL --> n4[selector]:::VARIABLE
    n4[selector]:::VARIABLE --> n10[==]:::BIN_OP
    n4[selector]:::VARIABLE --> n14[==]:::BIN_OP
    n4[selector]:::VARIABLE --> n20[==]:::BIN_OP
    n5[0]:::LITERAL --> n6[chosen]:::VARIABLE
    n7[200]:::LITERAL --> n8[side]:::VARIABLE
    n9[1]:::LITERAL --> n10[==]:::BIN_OP
    n11[10]:::LITERAL --> n12[chosen]:::VARIABLE
    n13[2]:::LITERAL --> n14[==]:::BIN_OP
    n15[20]:::LITERAL --> n16[chosen]:::VARIABLE
    n17[300]:::LITERAL --> n18[side]:::VARIABLE
    n19[3]:::LITERAL --> n20[==]:::BIN_OP
    n21[30]:::LITERAL --> n22[chosen]:::VARIABLE
    n23[100]:::LITERAL --> n24[chosen]:::VARIABLE
    n24[chosen]:::VARIABLE --> n27[+]:::BIN_OP
    n25[500]:::LITERAL --> n26[side]:::VARIABLE
    n26[side]:::VARIABLE --> n27[+]:::BIN_OP
    n27[+]:::BIN_OP --> n28[out]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
