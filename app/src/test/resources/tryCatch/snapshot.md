```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[1]:::LITERAL
    n5[attempts]:::VARIABLE
    n6[0]:::LITERAL
    n7[index]:::BIN_OP
    n8[parseInt]:::EXTERNAL
    n9[result]:::VARIABLE
    n10[failure]:::OBJ_VARIABLE
    n11[result]:::VARIABLE
    n12[result]:::VARIABLE
    n13[reported]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[1]:::LITERAL
    n1[event main]:::EVENT -. event .-> n6[0]:::LITERAL
    n1[event main]:::EVENT -. event .-> n10[failure]:::OBJ_VARIABLE
    n3[args]:::FUNC_PARAM --> n7[index]:::BIN_OP
    n4[1]:::LITERAL --> n5[attempts]:::VARIABLE
    n5[attempts]:::VARIABLE --> n11[result]:::VARIABLE
    n6[0]:::LITERAL --> n7[index]:::BIN_OP
    n7[index]:::BIN_OP --> n8[parseInt]:::EXTERNAL
    n8[parseInt]:::EXTERNAL --> n9[result]:::VARIABLE
    n9[result]:::VARIABLE --> n12[result]:::VARIABLE
    n11[result]:::VARIABLE --> n12[result]:::VARIABLE
    n12[result]:::VARIABLE --> n13[reported]:::VARIABLE
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
