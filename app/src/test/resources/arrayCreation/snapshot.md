```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[2]:::LITERAL
    n5[size]:::VARIABLE
    n6[array]:::BIN_OP
    n7[sized]:::OBJ_VARIABLE
    n8[7]:::LITERAL
    n9[seed]:::VARIABLE
    n10[9]:::LITERAL
    n11[array]:::BIN_OP
    n12[filled]:::OBJ_VARIABLE
    n13[0]:::LITERAL
    n14[index]:::BIN_OP
    n15[1]:::LITERAL
    n16[index]:::BIN_OP
    n17[+]:::BIN_OP
    n18[out]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[2]:::LITERAL
    n1[event main]:::EVENT -. event .-> n8[7]:::LITERAL
    n1[event main]:::EVENT -. event .-> n10[9]:::LITERAL
    n1[event main]:::EVENT -. event .-> n13[0]:::LITERAL
    n1[event main]:::EVENT -. event .-> n15[1]:::LITERAL
    n4[2]:::LITERAL --> n5[size]:::VARIABLE
    n5[size]:::VARIABLE --> n6[array]:::BIN_OP
    n6[array]:::BIN_OP --> n7[sized]:::OBJ_VARIABLE
    n7[sized]:::OBJ_VARIABLE --> n16[index]:::BIN_OP
    n8[7]:::LITERAL --> n9[seed]:::VARIABLE
    n9[seed]:::VARIABLE --> n11[array]:::BIN_OP
    n10[9]:::LITERAL --> n11[array]:::BIN_OP
    n11[array]:::BIN_OP --> n12[filled]:::OBJ_VARIABLE
    n12[filled]:::OBJ_VARIABLE --> n14[index]:::BIN_OP
    n13[0]:::LITERAL --> n14[index]:::BIN_OP
    n14[index]:::BIN_OP --> n17[+]:::BIN_OP
    n15[1]:::LITERAL --> n16[index]:::BIN_OP
    n16[index]:::BIN_OP --> n17[+]:::BIN_OP
    n17[+]:::BIN_OP --> n18[out]:::VARIABLE
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  linkStyle 4 stroke:#00897b,color:#00897b
  linkStyle 5 stroke:#00897b,color:#00897b
  linkStyle 6 stroke:#00897b,color:#00897b
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
  classDef EVENT fill:#00897B40
```
