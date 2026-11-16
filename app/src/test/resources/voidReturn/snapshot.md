```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[Sink]:::EXTERNAL
    n5[sink]:::OBJ_VARIABLE
    n6[5]:::LITERAL
    n13[out]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[Sink]:::EXTERNAL
    n1[event main]:::EVENT -. event .-> n6[5]:::LITERAL
    n1[event main]:::EVENT -. event .-> n8[store]:::RETURN
    n1[event main]:::EVENT -. event .-> n10[0]:::LITERAL
    n4[Sink]:::EXTERNAL --> n5[sink]:::OBJ_VARIABLE
    n6[5]:::LITERAL --> n9[seed]:::FUNC_PARAM
    n12[held]:::VARIABLE --> n13[out]:::VARIABLE
    subgraph b7["store"]
      n8[store]:::RETURN
      n9[seed]:::FUNC_PARAM
      n10[0]:::LITERAL
      n11[<]:::BIN_OP
      n12[held]:::VARIABLE
      n9[seed]:::FUNC_PARAM --> n11[<]:::BIN_OP
      n9[seed]:::FUNC_PARAM --> n12[held]:::VARIABLE
      n10[0]:::LITERAL --> n11[<]:::BIN_OP
    end
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  linkStyle 4 stroke:#00897b,color:#00897b
  linkStyle 5 stroke:#00897b,color:#00897b
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
  classDef EVENT fill:#00897B40
```
