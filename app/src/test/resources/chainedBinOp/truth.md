```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[a]:::VARIABLE
    n4[10]:::LITERAL
    n5[b]:::VARIABLE
    n6[2]:::LITERAL
    n7[c]:::VARIABLE
    n8[3]:::LITERAL
    n9[chained]:::VARIABLE
    n10[+]:::BIN_OP
    n11[+]:::BIN_OP
    n12[nested]:::VARIABLE
    n13[-]:::BIN_OP
    n14[-]:::BIN_OP
    n3[a]:::VARIABLE --> n10[+]:::BIN_OP
    n3[a]:::VARIABLE --> n13[-]:::BIN_OP
    n4[10]:::LITERAL --> n3[a]:::VARIABLE
    n5[b]:::VARIABLE --> n10[+]:::BIN_OP
    n5[b]:::VARIABLE --> n13[-]:::BIN_OP
    n6[2]:::LITERAL --> n5[b]:::VARIABLE
    n7[c]:::VARIABLE --> n11[+]:::BIN_OP
    n7[c]:::VARIABLE --> n14[-]:::BIN_OP
    n8[3]:::LITERAL --> n7[c]:::VARIABLE
    n10[+]:::BIN_OP --> n11[+]:::BIN_OP
    n11[+]:::BIN_OP --> n9[chained]:::VARIABLE
    n13[-]:::BIN_OP --> n14[-]:::BIN_OP
    n14[-]:::BIN_OP --> n12[nested]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
