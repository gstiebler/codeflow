```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[adder]:::OBJ_VARIABLE
    n4[Adder]:::EXTERNAL
    n5[sum]:::VARIABLE
    n6[3]:::LITERAL
    n7[5]:::LITERAL
    n4[Adder]:::EXTERNAL --> n3[adder]:::OBJ_VARIABLE
    n6[3]:::LITERAL --> n10[left]:::FUNC_PARAM
    n7[5]:::LITERAL --> n11[right]:::FUNC_PARAM
    subgraph b8["add"]
      n9[add]:::RETURN
      n10[left]:::FUNC_PARAM
      n11[right]:::FUNC_PARAM
      n12[+]:::BIN_OP
      n9[add]:::RETURN --> n5[sum]:::VARIABLE
      n10[left]:::FUNC_PARAM --> n12[+]:::BIN_OP
      n11[right]:::FUNC_PARAM --> n12[+]:::BIN_OP
      n12[+]:::BIN_OP --> n9[add]:::RETURN
    end
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
