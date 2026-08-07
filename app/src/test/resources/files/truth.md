```mermaid
flowchart TD
  subgraph b0["main"]
    n1[main]:::RETURN
    n2[args]:::FUNC_PARAM
    n3[x]:::OBJ_VARIABLE
    n4[ClassX]:::EXTERNAL
    n5[y]:::OBJ_VARIABLE
    n6[ClassY]:::EXTERNAL
    n7[zClass]:::OBJ_VARIABLE
    n8[ClassX]:::EXTERNAL
    n9[memberX]:::VARIABLE
    n10[6]:::LITERAL
    n11[memberX]:::VARIABLE
    n12[5]:::LITERAL
    n13[memberY]:::VARIABLE
    n14[y2]:::OBJ_VARIABLE
    n15[z]:::VARIABLE
    n4[ClassX]:::EXTERNAL --> n3[x]:::OBJ_VARIABLE
    n5[y]:::OBJ_VARIABLE --> n14[y2]:::OBJ_VARIABLE
    n6[ClassY]:::EXTERNAL --> n5[y]:::OBJ_VARIABLE
    n8[ClassX]:::EXTERNAL --> n7[zClass]:::OBJ_VARIABLE
    n10[6]:::LITERAL --> n9[memberX]:::VARIABLE
    n11[memberX]:::VARIABLE --> n13[memberY]:::VARIABLE
    n12[5]:::LITERAL --> n11[memberX]:::VARIABLE
    n13[memberY]:::VARIABLE --> n15[z]:::VARIABLE
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
```
