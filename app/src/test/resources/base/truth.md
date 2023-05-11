```mermaid
flowchart TD
  subgraph main
    2[a]:::VARIABLE --> 4[b]:::VARIABLE
    3[5]:::LITERAL --> 2[a]:::VARIABLE
    4[b]:::VARIABLE --> 7[+]:::BIN_OP
    4[b]:::VARIABLE --> 8[d]:::VARIABLE
    6[8]:::LITERAL --> 7[+]:::BIN_OP
    7[+]:::BIN_OP --> 5[c]:::VARIABLE
    8[d]:::VARIABLE --> 9[e]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
```
