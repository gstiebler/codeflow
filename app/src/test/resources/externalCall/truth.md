```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[5]:::LITERAL
    n4[a]:::VARIABLE
    n5[abs]:::EXTERNAL
    n6[b]:::VARIABLE
    n7[out]:::EXTERNAL
    n8[println]:::EXTERNAL
    n9[1]:::LITERAL
    n10[+]:::BIN_OP
    n11[c]:::VARIABLE
    n3[5]:::LITERAL --> n4[a]:::VARIABLE
    n4[a]:::VARIABLE --> n5[abs]:::EXTERNAL
    n5[abs]:::EXTERNAL --> n6[b]:::VARIABLE
    n6[b]:::VARIABLE --> n8[println]:::EXTERNAL
    n6[b]:::VARIABLE --> n10[+]:::BIN_OP
    n7[out]:::EXTERNAL --> n8[println]:::EXTERNAL
    n9[1]:::LITERAL --> n10[+]:::BIN_OP
    n10[+]:::BIN_OP --> n11[c]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
