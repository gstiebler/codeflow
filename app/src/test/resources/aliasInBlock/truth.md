```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[box]:::OBJ_VARIABLE
    n4[Box]:::EXTERNAL
    n5[value]:::VARIABLE
    n6[1]:::LITERAL
    n7[alias]:::OBJ_VARIABLE
    n8[value]:::VARIABLE
    n9[7]:::LITERAL
    n10[read]:::VARIABLE
    n3[box]:::OBJ_VARIABLE --> n7[alias]:::OBJ_VARIABLE
    n4[Box]:::EXTERNAL --> n3[box]:::OBJ_VARIABLE
    n6[1]:::LITERAL --> n5[value]:::VARIABLE
    n8[value]:::VARIABLE --> n10[read]:::VARIABLE
    n9[7]:::LITERAL --> n8[value]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
