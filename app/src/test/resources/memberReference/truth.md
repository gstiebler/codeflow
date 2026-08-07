```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[names]:::OBJ_VARIABLE
    n4["a"]:::LITERAL
    n5[of]:::EXTERNAL
    n6[counter]:::OBJ_VARIABLE
    n7[size]:::EXTERNAL
    n8[out]:::VARIABLE
    n9[get]:::EXTERNAL
    n3[names]:::OBJ_VARIABLE --> n7[size]:::EXTERNAL
    n4["a"]:::LITERAL --> n5[of]:::EXTERNAL
    n5[of]:::EXTERNAL --> n3[names]:::OBJ_VARIABLE
    n6[counter]:::OBJ_VARIABLE --> n9[get]:::EXTERNAL
    n7[size]:::EXTERNAL --> n6[counter]:::OBJ_VARIABLE
    n9[get]:::EXTERNAL --> n8[out]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
