```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[of]:::EXTERNAL
    n4[items]:::OBJ_VARIABLE
    n5[stream]:::EXTERNAL
    n6[toList]:::EXTERNAL
    n7[size]:::EXTERNAL
    n8[size]:::VARIABLE
    n3[of]:::EXTERNAL --> n4[items]:::OBJ_VARIABLE
    n4[items]:::OBJ_VARIABLE --> n5[stream]:::EXTERNAL
    n5[stream]:::EXTERNAL --> n6[toList]:::EXTERNAL
    n6[toList]:::EXTERNAL --> n7[size]:::EXTERNAL
    n7[size]:::EXTERNAL --> n8[size]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
