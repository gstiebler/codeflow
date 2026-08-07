```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[0]:::LITERAL
    n4[out]:::VARIABLE
    n5[0]:::LITERAL
    n6[index]:::BIN_OP
    n7[parseInt]:::EXTERNAL
    n8[out]:::VARIABLE
    n9[failure]:::OBJ_VARIABLE
    n10[getMessage]:::EXTERNAL
    n11[reason]:::OBJ_VARIABLE
    n12[length]:::EXTERNAL
    n13[out]:::VARIABLE
    n2[args]:::FUNC_PARAM --> n6[index]:::BIN_OP
    n3[0]:::LITERAL --> n4[out]:::VARIABLE
    n5[0]:::LITERAL --> n6[index]:::BIN_OP
    n6[index]:::BIN_OP --> n7[parseInt]:::EXTERNAL
    n7[parseInt]:::EXTERNAL --> n8[out]:::VARIABLE
    n9[failure]:::OBJ_VARIABLE --> n10[getMessage]:::EXTERNAL
    n10[getMessage]:::EXTERNAL --> n11[reason]:::OBJ_VARIABLE
    n11[reason]:::OBJ_VARIABLE --> n12[length]:::EXTERNAL
    n12[length]:::EXTERNAL --> n13[out]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
