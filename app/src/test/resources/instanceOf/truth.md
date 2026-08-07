```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[value]:::OBJ_VARIABLE
    n4[isText]:::VARIABLE
    n5[instanceof]:::BIN_OP
    n6[text]:::OBJ_VARIABLE
    n7[instanceof]:::BIN_OP
    n8[size]:::VARIABLE
    n9[length]:::EXTERNAL
    n2[args]:::FUNC_PARAM --> n3[value]:::OBJ_VARIABLE
    n3[value]:::OBJ_VARIABLE --> n5[instanceof]:::BIN_OP
    n3[value]:::OBJ_VARIABLE --> n6[text]:::OBJ_VARIABLE
    n3[value]:::OBJ_VARIABLE --> n7[instanceof]:::BIN_OP
    n5[instanceof]:::BIN_OP --> n4[isText]:::VARIABLE
    n6[text]:::OBJ_VARIABLE --> n9[length]:::EXTERNAL
    n9[length]:::EXTERNAL --> n8[size]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
