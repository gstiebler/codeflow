```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[items]:::OBJ_VARIABLE
    n4[of]:::EXTERNAL
    n5[size]:::VARIABLE
    n6[stream]:::EXTERNAL
    n7[toList]:::EXTERNAL
    n8[size]:::EXTERNAL
    n3[items]:::OBJ_VARIABLE --> n6[stream]:::EXTERNAL
    n4[of]:::EXTERNAL --> n3[items]:::OBJ_VARIABLE
    n6[stream]:::EXTERNAL --> n7[toList]:::EXTERNAL
    n7[toList]:::EXTERNAL --> n8[size]:::EXTERNAL
    n8[size]:::EXTERNAL --> n5[size]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
