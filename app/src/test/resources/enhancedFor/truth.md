```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[names]:::OBJ_VARIABLE
    n4["a"]:::LITERAL
    n5[of]:::EXTERNAL
    n6[total]:::VARIABLE
    n7[0]:::LITERAL
    n8[name]:::OBJ_VARIABLE
    n9[size]:::VARIABLE
    n10[length]:::EXTERNAL
    n11[total]:::VARIABLE
    n12[+]:::BIN_OP
    n3[names]:::OBJ_VARIABLE --> n8[name]:::OBJ_VARIABLE
    n4["a"]:::LITERAL --> n5[of]:::EXTERNAL
    n5[of]:::EXTERNAL --> n3[names]:::OBJ_VARIABLE
    n7[0]:::LITERAL --> n6[total]:::VARIABLE
    n8[name]:::OBJ_VARIABLE --> n10[length]:::EXTERNAL
    n9[size]:::VARIABLE --> n12[+]:::BIN_OP
    n10[length]:::EXTERNAL --> n9[size]:::VARIABLE
    n11[total]:::VARIABLE --> n12[+]:::BIN_OP
    n12[+]:::BIN_OP --> n11[total]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
