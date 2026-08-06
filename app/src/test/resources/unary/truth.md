```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[value]:::VARIABLE
    n4[7]:::LITERAL
    n5[flag]:::VARIABLE
    n6[true]:::LITERAL
    n7[negated]:::VARIABLE
    n8[neg]:::BIN_OP
    n9[inverted]:::VARIABLE
    n10[not]:::BIN_OP
    n11[counter]:::VARIABLE
    n12[0]:::LITERAL
    n13[postInc]:::BIN_OP
    n14[afterIncrement]:::VARIABLE
    n3[value]:::VARIABLE --> n8[neg]:::BIN_OP
    n4[7]:::LITERAL --> n3[value]:::VARIABLE
    n5[flag]:::VARIABLE --> n10[not]:::BIN_OP
    n6[true]:::LITERAL --> n5[flag]:::VARIABLE
    n8[neg]:::BIN_OP --> n7[negated]:::VARIABLE
    n10[not]:::BIN_OP --> n9[inverted]:::VARIABLE
    n11[counter]:::VARIABLE --> n13[postInc]:::BIN_OP
    n11[counter]:::VARIABLE --> n14[afterIncrement]:::VARIABLE
    n12[0]:::LITERAL --> n11[counter]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
