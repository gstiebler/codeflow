```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[value]:::VARIABLE
    n5[+]:::BIN_OP
    n6[lambda]:::BIN_OP
    n7[twice]:::OBJ_VARIABLE
    n8[4]:::LITERAL
    n9[applyAsInt]:::EXTERNAL
    n10[out]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[value]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n8[4]:::LITERAL
    n4[value]:::VARIABLE --> n5[+]:::BIN_OP
    n4[value]:::VARIABLE --> n5[+]:::BIN_OP
    n5[+]:::BIN_OP --> n6[lambda]:::BIN_OP
    n6[lambda]:::BIN_OP --> n7[twice]:::OBJ_VARIABLE
    n7[twice]:::OBJ_VARIABLE --> n9[applyAsInt]:::EXTERNAL
    n8[4]:::LITERAL --> n9[applyAsInt]:::EXTERNAL
    n9[applyAsInt]:::EXTERNAL --> n10[out]:::VARIABLE
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
  classDef EVENT fill:#00897B40
```
