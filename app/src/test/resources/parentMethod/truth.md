```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[Child]:::EXTERNAL
    n4[child]:::OBJ_VARIABLE
    n5[30]:::LITERAL
    n22[out]:::VARIABLE
    n3[Child]:::EXTERNAL --> n4[child]:::OBJ_VARIABLE
    n5[30]:::LITERAL --> n8[input]:::FUNC_PARAM
    subgraph b6["adjust"]
      n7[adjust]:::RETURN
      n8[input]:::FUNC_PARAM
      n9[5]:::LITERAL
      n10[offset]:::VARIABLE
      n15[shifted]:::VARIABLE
      n21[scaled]:::VARIABLE
      n7[adjust]:::RETURN --> n22[out]:::VARIABLE
      n8[input]:::FUNC_PARAM --> n13[amount]:::FUNC_PARAM
      n9[5]:::LITERAL --> n10[offset]:::VARIABLE
      n10[offset]:::VARIABLE --> n14[+]:::BIN_OP
      n15[shifted]:::VARIABLE --> n18[factor]:::FUNC_PARAM
      n21[scaled]:::VARIABLE --> n7[adjust]:::RETURN
      subgraph b11["shift"]
        n12[shift]:::RETURN
        n13[amount]:::FUNC_PARAM
        n14[+]:::BIN_OP
        n12[shift]:::RETURN --> n15[shifted]:::VARIABLE
        n13[amount]:::FUNC_PARAM --> n14[+]:::BIN_OP
        n14[+]:::BIN_OP --> n12[shift]:::RETURN
      end
      subgraph b16["scale"]
        n17[scale]:::RETURN
        n18[factor]:::FUNC_PARAM
        n19[3]:::LITERAL
        n20[*]:::BIN_OP
        n17[scale]:::RETURN --> n21[scaled]:::VARIABLE
        n18[factor]:::FUNC_PARAM --> n20[*]:::BIN_OP
        n19[3]:::LITERAL --> n20[*]:::BIN_OP
        n20[*]:::BIN_OP --> n17[scale]:::RETURN
      end
    end
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
