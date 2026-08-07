```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[base]:::VARIABLE
    n4[3]:::LITERAL
    n5[scale]:::OBJ_VARIABLE
    n6[value]:::OBJ_VARIABLE
    n7[*]:::BIN_OP
    n8[lambda]:::BIN_OP
    n9[out]:::VARIABLE
    n10[4]:::LITERAL
    n11[applyAsInt]:::EXTERNAL
    n3[base]:::VARIABLE --> n7[*]:::BIN_OP
    n4[3]:::LITERAL --> n3[base]:::VARIABLE
    n5[scale]:::OBJ_VARIABLE --> n11[applyAsInt]:::EXTERNAL
    n6[value]:::OBJ_VARIABLE --> n7[*]:::BIN_OP
    n7[*]:::BIN_OP --> n8[lambda]:::BIN_OP
    n8[lambda]:::BIN_OP --> n5[scale]:::OBJ_VARIABLE
    n10[4]:::LITERAL --> n11[applyAsInt]:::EXTERNAL
    n11[applyAsInt]:::EXTERNAL --> n9[out]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
