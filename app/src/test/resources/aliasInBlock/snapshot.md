```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[Box]:::EXTERNAL
    n5[box]:::OBJ_VARIABLE
    n6[1]:::LITERAL
    n7[value]:::VARIABLE
    n8[alias]:::OBJ_VARIABLE
    n9[7]:::LITERAL
    n10[value]:::VARIABLE
    n11[read]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[Box]:::EXTERNAL
    n1[event main]:::EVENT -. event .-> n6[1]:::LITERAL
    n1[event main]:::EVENT -. event .-> n9[7]:::LITERAL
    n4[Box]:::EXTERNAL --> n5[box]:::OBJ_VARIABLE
    n5[box]:::OBJ_VARIABLE --> n8[alias]:::OBJ_VARIABLE
    n6[1]:::LITERAL --> n7[value]:::VARIABLE
    n9[7]:::LITERAL --> n10[value]:::VARIABLE
    n10[value]:::VARIABLE --> n11[read]:::VARIABLE
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
