```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[out]:::VARIABLE
    n4[1]:::LITERAL
    n5[2]:::LITERAL
    n6[3]:::LITERAL
    n4[1]:::LITERAL --> n9[base]:::FUNC_PARAM
    n5[2]:::LITERAL --> n10[rest]:::FUNC_PARAM
    n6[3]:::LITERAL --> n10[rest]:::FUNC_PARAM
    subgraph b7["total"]
      n8[total]:::RETURN
      n9[base]:::FUNC_PARAM
      n10[rest]:::FUNC_PARAM
      n11[sum]:::VARIABLE
      n12[part]:::VARIABLE
      n13[sum]:::VARIABLE
      n14[+]:::BIN_OP
      n8[total]:::RETURN --> n3[out]:::VARIABLE
      n9[base]:::FUNC_PARAM --> n11[sum]:::VARIABLE
      n10[rest]:::FUNC_PARAM --> n12[part]:::VARIABLE
      n12[part]:::VARIABLE --> n14[+]:::BIN_OP
      n13[sum]:::VARIABLE --> n14[+]:::BIN_OP
      n13[sum]:::VARIABLE --> n8[total]:::RETURN
      n14[+]:::BIN_OP --> n13[sum]:::VARIABLE
    end
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
