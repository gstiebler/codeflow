```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[TYPE_CAST]:::UNMODELLED
    n4[toString]:::EXTERNAL
    n5[out]:::OBJ_VARIABLE
    n2[args]:::FUNC_PARAM --> n3[TYPE_CAST]:::UNMODELLED
    n3[TYPE_CAST]:::UNMODELLED --> n4[toString]:::EXTERNAL
    n4[toString]:::EXTERNAL --> n5[out]:::OBJ_VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
