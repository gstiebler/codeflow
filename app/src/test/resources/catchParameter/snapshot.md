```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[0]:::LITERAL
    n5[out]:::VARIABLE
    n6[0]:::LITERAL
    n7[index]:::BIN_OP
    n8[parseInt]:::EXTERNAL
    n9[out]:::VARIABLE
    n10[out]:::VARIABLE
    n11[failure]:::OBJ_VARIABLE
    n12[getMessage]:::EXTERNAL
    n13[reason]:::OBJ_VARIABLE
    n14[length]:::EXTERNAL
    n15[out]:::VARIABLE
    n16[out]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[0]:::LITERAL
    n1[event main]:::EVENT -. event .-> n6[0]:::LITERAL
    n1[event main]:::EVENT -. event .-> n11[failure]:::OBJ_VARIABLE
    n3[args]:::FUNC_PARAM --> n7[index]:::BIN_OP
    n4[0]:::LITERAL --> n5[out]:::VARIABLE
    n5[out]:::VARIABLE --> n10[out]:::VARIABLE
    n6[0]:::LITERAL --> n7[index]:::BIN_OP
    n7[index]:::BIN_OP --> n8[parseInt]:::EXTERNAL
    n8[parseInt]:::EXTERNAL --> n9[out]:::VARIABLE
    n9[out]:::VARIABLE --> n10[out]:::VARIABLE
    n9[out]:::VARIABLE --> n16[out]:::VARIABLE
    n11[failure]:::OBJ_VARIABLE --> n12[getMessage]:::EXTERNAL
    n12[getMessage]:::EXTERNAL --> n13[reason]:::OBJ_VARIABLE
    n13[reason]:::OBJ_VARIABLE --> n14[length]:::EXTERNAL
    n14[length]:::EXTERNAL --> n15[out]:::VARIABLE
    n15[out]:::VARIABLE --> n16[out]:::VARIABLE
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
