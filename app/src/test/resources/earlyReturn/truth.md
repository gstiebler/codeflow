```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[result]:::VARIABLE
    n4[70]:::LITERAL
    n4[70]:::LITERAL --> n7[score]:::FUNC_PARAM
    subgraph b5["classify"]
      n6[classify]:::RETURN
      n7[score]:::FUNC_PARAM
      n8[90]:::LITERAL
      n9[>]:::BIN_OP
      n10[100]:::LITERAL
      n11[50]:::LITERAL
      n12[>]:::BIN_OP
      n13[55]:::LITERAL
      n14[floor]:::VARIABLE
      n15[10]:::LITERAL
      n16[scale]:::VARIABLE
      n17[2]:::LITERAL
      n18[*]:::BIN_OP
      n6[classify]:::RETURN --> n3[result]:::VARIABLE
      n7[score]:::FUNC_PARAM --> n9[>]:::BIN_OP
      n7[score]:::FUNC_PARAM --> n12[>]:::BIN_OP
      n8[90]:::LITERAL --> n9[>]:::BIN_OP
      n10[100]:::LITERAL --> n6[classify]:::RETURN
      n11[50]:::LITERAL --> n12[>]:::BIN_OP
      n13[55]:::LITERAL --> n6[classify]:::RETURN
      n14[floor]:::VARIABLE --> n18[*]:::BIN_OP
      n15[10]:::LITERAL --> n14[floor]:::VARIABLE
      n16[scale]:::VARIABLE --> n18[*]:::BIN_OP
      n17[2]:::LITERAL --> n16[scale]:::VARIABLE
      n18[*]:::BIN_OP --> n6[classify]:::RETURN
    end
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
