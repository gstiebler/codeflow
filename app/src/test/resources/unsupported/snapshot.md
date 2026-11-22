```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[out]:::EXTERNAL
    n5["x"]:::LITERAL
    n6[println]:::EXTERNAL
    n7[shape]:::OBJ_VARIABLE
    n8[DECONSTRUCTION_PATTERN]:::UNMODELLED
    n9[instanceof]:::BIN_OP
    n10["point"]:::LITERAL
    n11[println]:::EXTERNAL
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[out]:::EXTERNAL
    n1[event main]:::EVENT -. event .-> n5["x"]:::LITERAL
    n1[event main]:::EVENT -. event .-> n10["point"]:::LITERAL
    n3[args]:::FUNC_PARAM --> n7[shape]:::OBJ_VARIABLE
    n4[out]:::EXTERNAL --> n6[println]:::EXTERNAL
    n4[out]:::EXTERNAL --> n11[println]:::EXTERNAL
    n5["x"]:::LITERAL --> n6[println]:::EXTERNAL
    n7[shape]:::OBJ_VARIABLE --> n8[DECONSTRUCTION_PATTERN]:::UNMODELLED
    n7[shape]:::OBJ_VARIABLE --> n9[instanceof]:::BIN_OP
    n10["point"]:::LITERAL --> n11[println]:::EXTERNAL
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  linkStyle 4 stroke:#00897b,color:#00897b
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
  classDef EVENT fill:#00897B40
```
