```mermaid
flowchart TD
  subgraph b0["main"]
    n1[event main]:::EVENT
    n2[main]:::RETURN
    n3[args]:::FUNC_PARAM
    n4[10]:::LITERAL
    n11[counter]:::OBJ_VARIABLE
    n15[result]:::VARIABLE
    n1[event main]:::EVENT -. event .-> n2[main]:::RETURN
    n1[event main]:::EVENT -. event .-> n3[args]:::FUNC_PARAM
    n1[event main]:::EVENT -. event .-> n4[10]:::LITERAL
    n1[event main]:::EVENT -. event .-> n6[<init>]:::RETURN
    n1[event main]:::EVENT -. event .-> n9[3]:::LITERAL
    n4[10]:::LITERAL --> n7[initial]:::FUNC_PARAM
    n6[<init>]:::RETURN --> n11[counter]:::OBJ_VARIABLE
    n8[value]:::VARIABLE --> n14[+]:::BIN_OP
    n10[step]:::VARIABLE --> n14[+]:::BIN_OP
    n13[advance]:::RETURN --> n15[result]:::VARIABLE
    subgraph b5["Counter.constructor"]
      n6[<init>]:::RETURN
      n7[initial]:::FUNC_PARAM
      n8[value]:::VARIABLE
      n9[3]:::LITERAL
      n10[step]:::VARIABLE
      n7[initial]:::FUNC_PARAM --> n8[value]:::VARIABLE
      n9[3]:::LITERAL --> n10[step]:::VARIABLE
    end
    subgraph b12["advance"]
      n13[advance]:::RETURN
      n14[+]:::BIN_OP
      n14[+]:::BIN_OP --> n13[advance]:::RETURN
    end
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
