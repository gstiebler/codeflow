```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[5]:::LITERAL
    n4[x]:::VARIABLE
    n5[0]:::LITERAL
    n6[y]:::VARIABLE
    n7[0]:::LITERAL
    n8[i]:::VARIABLE
    n9[<]:::BIN_OP
    n10[postInc]:::BIN_OP
    n11[7]:::LITERAL
    n12[y]:::VARIABLE
    n13[z]:::VARIABLE
    n14[1]:::LITERAL
    n15[+=]:::BIN_OP
    n16[y]:::VARIABLE
    n3[5]:::LITERAL --> n4[x]:::VARIABLE
    n4[x]:::VARIABLE --> n9[<]:::BIN_OP
    n5[0]:::LITERAL --> n6[y]:::VARIABLE
    n7[0]:::LITERAL --> n8[i]:::VARIABLE
    n8[i]:::VARIABLE --> n9[<]:::BIN_OP
    n8[i]:::VARIABLE --> n10[postInc]:::BIN_OP
    n11[7]:::LITERAL --> n12[y]:::VARIABLE
    n12[y]:::VARIABLE --> n13[z]:::VARIABLE
    n12[y]:::VARIABLE --> n15[+=]:::BIN_OP
    n14[1]:::LITERAL --> n15[+=]:::BIN_OP
    n15[+=]:::BIN_OP --> n16[y]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
