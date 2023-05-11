```mermaid
flowchart TD
  subgraph main
    2[5]:::LITERAL --> 1[a]:::VARIABLE
    3[a]:::VARIABLE --> 5[memberA]:::VARIABLE
    4[6]:::LITERAL --> 3[a]:::VARIABLE
    5[memberA]:::VARIABLE --> 6[b]:::VARIABLE
    7[memberX]:::VARIABLE --> 9[c]:::VARIABLE
    7[memberX]:::VARIABLE --> 10[d]:::VARIABLE
    8[8]:::LITERAL --> 7[memberX]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
```
