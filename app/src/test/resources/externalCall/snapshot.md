```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[5]:::LITERAL
    n5[a]:::VARIABLE
    n6[abs]:::EXTERNAL
    n7[b]:::VARIABLE
    n8[out]:::EXTERNAL
    n9[println]:::EXTERNAL
    n10[1]:::LITERAL
    n11[+]:::BIN_OP
    n12[c]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[5]:::LITERAL
    n1[event main]:::EVENT -. event .-> n8[out]:::EXTERNAL
    n1[event main]:::EVENT -. event .-> n10[1]:::LITERAL
    n4[5]:::LITERAL --> n5[a]:::VARIABLE
    n5[a]:::VARIABLE --> n6[abs]:::EXTERNAL
    n6[abs]:::EXTERNAL --> n7[b]:::VARIABLE
    n7[b]:::VARIABLE --> n9[println]:::EXTERNAL
    n7[b]:::VARIABLE --> n11[+]:::BIN_OP
    n8[out]:::EXTERNAL --> n9[println]:::EXTERNAL
    n10[1]:::LITERAL --> n11[+]:::BIN_OP
    n11[+]:::BIN_OP --> n12[c]:::VARIABLE
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
