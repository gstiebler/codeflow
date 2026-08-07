```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[length]:::EXTERNAL
    n4[0]:::LITERAL
    n5[>]:::BIN_OP
    n6[flag]:::VARIABLE
    n7[11]:::LITERAL
    n12[22]:::LITERAL
    n17[ternary]:::BIN_OP
    n18[chosen]:::OBJ_VARIABLE
    n19[amount]:::VARIABLE
    n20[total]:::VARIABLE
    n3[length]:::EXTERNAL --> n5[>]:::BIN_OP
    n4[0]:::LITERAL --> n5[>]:::BIN_OP
    n5[>]:::BIN_OP --> n6[flag]:::VARIABLE
    n6[flag]:::VARIABLE --> n17[ternary]:::BIN_OP
    n7[11]:::LITERAL --> n10[amount]:::FUNC_PARAM
    n12[22]:::LITERAL --> n15[amount]:::FUNC_PARAM
    n17[ternary]:::BIN_OP --> n18[chosen]:::OBJ_VARIABLE
    n19[amount]:::VARIABLE --> n20[total]:::VARIABLE
    subgraph b8["Holder.constructor"]
      n9[<init>]:::RETURN
      n10[amount]:::FUNC_PARAM
      n11[amount]:::VARIABLE
      n9[<init>]:::RETURN --> n17[ternary]:::BIN_OP
      n10[amount]:::FUNC_PARAM --> n11[amount]:::VARIABLE
      n11[amount]:::VARIABLE --> n19[amount]:::VARIABLE
    end
    subgraph b13["Holder.constructor"]
      n14[<init>]:::RETURN
      n15[amount]:::FUNC_PARAM
      n16[amount]:::VARIABLE
      n14[<init>]:::RETURN --> n17[ternary]:::BIN_OP
      n15[amount]:::FUNC_PARAM --> n16[amount]:::VARIABLE
      n16[amount]:::VARIABLE --> n19[amount]:::VARIABLE
    end
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
