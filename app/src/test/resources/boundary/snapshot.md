```mermaid
flowchart TD
  subgraph b0["entrypoint"]
    n1[event entrypoint]:::EVENT
    n2[entrypoint]:::RETURN
    n3[a]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM
    n5[c]:::FUNC_PARAM
    n13[result1]:::VARIABLE
    n14[0]:::LITERAL
    n15[result2]:::VARIABLE
    n16[>]:::BIN_OP
    n24[result2]:::VARIABLE
    n48[if]:::BIN_OP
    n49[result2]:::VARIABLE
    n50[if]:::BIN_OP
    n51[boundaries entrypoint]:::BOUNDARIES
    n1[event entrypoint]:::EVENT -. event .-> n2[entrypoint]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n3[a]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n4[b]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n5[c]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n14[0]:::LITERAL
    n1[event entrypoint]:::EVENT -. event .-> n9[boundaries entrypoint]:::BOUNDARIES
    n3[a]:::FUNC_PARAM --> n8[x]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n16[>]:::BIN_OP
    n3[a]:::FUNC_PARAM --> n20[y]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n35[x]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n44[y]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n16[>]:::BIN_OP
    n4[b]:::FUNC_PARAM --> n28[y]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n36[y]:::FUNC_PARAM
    n5[c]:::FUNC_PARAM --> n29[z]:::FUNC_PARAM
    n5[c]:::FUNC_PARAM --> n37[z]:::FUNC_PARAM
    n13[result1]:::VARIABLE --> n19[x]:::FUNC_PARAM
    n14[0]:::LITERAL --> n15[result2]:::VARIABLE
    n15[result2]:::VARIABLE --> n43[x]:::FUNC_PARAM
    n15[result2]:::VARIABLE -->|false| n48[if]:::BIN_OP
    n16[>]:::BIN_OP -->|if| n48[if]:::BIN_OP
    n16[>]:::BIN_OP -->|if| n50[if]:::BIN_OP
    n24[result2]:::VARIABLE --> n27[x]:::FUNC_PARAM
    n24[result2]:::VARIABLE -->|true| n48[if]:::BIN_OP
    n48[if]:::BIN_OP --> n49[result2]:::VARIABLE
    n50[if]:::BIN_OP --> n51[boundaries entrypoint]:::BOUNDARIES
    n7[boundaryfunc1]:::RETURN --> n13[result1]:::VARIABLE
    n11[boundaries entrypoint]:::BOUNDARIES -. order .-> n21["`**call** boundaryfunc2`"]:::EXTERNAL
    n11[boundaries entrypoint]:::BOUNDARIES -. order .-> n38["`**call** boundaryfunc3`"]:::EXTERNAL
    n18[boundaryfunc2]:::RETURN --> n24[result2]:::VARIABLE
    n22[boundaries entrypoint]:::BOUNDARIES -. order .-> n30["`**call** boundaryfunc3`"]:::EXTERNAL
    n31[boundaries entrypoint]:::BOUNDARIES -->|true| n50[if]:::BIN_OP
    n39[boundaries entrypoint]:::BOUNDARIES -. order .-> n45["`**call** boundaryfunc2`"]:::EXTERNAL
    n46[boundaries entrypoint]:::BOUNDARIES -->|false| n50[if]:::BIN_OP
    subgraph b6["boundaryfunc1"]
      n7[boundaryfunc1]:::RETURN
      n8[x]:::FUNC_PARAM
      n9[boundaries entrypoint]:::BOUNDARIES
      n10["`**call** boundaryfunc1`"]:::EXTERNAL
      n11[boundaries entrypoint]:::BOUNDARIES
      n12[result]:::VARIABLE
      n8[x]:::FUNC_PARAM --> n10["`**call** boundaryfunc1`"]:::EXTERNAL
      n9[boundaries entrypoint]:::BOUNDARIES -. order .-> n10["`**call** boundaryfunc1`"]:::EXTERNAL
      n10["`**call** boundaryfunc1`"]:::EXTERNAL -. order .-> n11[boundaries entrypoint]:::BOUNDARIES
      n10["`**call** boundaryfunc1`"]:::EXTERNAL --> n12[result]:::VARIABLE
      n12[result]:::VARIABLE --> n7[boundaryfunc1]:::RETURN
    end
    subgraph b17["boundaryfunc2"]
      n18[boundaryfunc2]:::RETURN
      n19[x]:::FUNC_PARAM
      n20[y]:::FUNC_PARAM
      n21["`**call** boundaryfunc2`"]:::EXTERNAL
      n22[boundaries entrypoint]:::BOUNDARIES
      n23[result]:::VARIABLE
      n19[x]:::FUNC_PARAM --> n21["`**call** boundaryfunc2`"]:::EXTERNAL
      n20[y]:::FUNC_PARAM --> n21["`**call** boundaryfunc2`"]:::EXTERNAL
      n21["`**call** boundaryfunc2`"]:::EXTERNAL -. order .-> n22[boundaries entrypoint]:::BOUNDARIES
      n21["`**call** boundaryfunc2`"]:::EXTERNAL --> n23[result]:::VARIABLE
      n23[result]:::VARIABLE --> n18[boundaryfunc2]:::RETURN
    end
    subgraph b25["boundaryfunc3"]
      n26[boundaryfunc3]:::RETURN
      n27[x]:::FUNC_PARAM
      n28[y]:::FUNC_PARAM
      n29[z]:::FUNC_PARAM
      n30["`**call** boundaryfunc3`"]:::EXTERNAL
      n31[boundaries entrypoint]:::BOUNDARIES
      n32[result]:::VARIABLE
      n27[x]:::FUNC_PARAM --> n30["`**call** boundaryfunc3`"]:::EXTERNAL
      n28[y]:::FUNC_PARAM --> n30["`**call** boundaryfunc3`"]:::EXTERNAL
      n29[z]:::FUNC_PARAM --> n30["`**call** boundaryfunc3`"]:::EXTERNAL
      n30["`**call** boundaryfunc3`"]:::EXTERNAL -. order .-> n31[boundaries entrypoint]:::BOUNDARIES
      n30["`**call** boundaryfunc3`"]:::EXTERNAL --> n32[result]:::VARIABLE
      n32[result]:::VARIABLE --> n26[boundaryfunc3]:::RETURN
    end
    subgraph b33["boundaryfunc3"]
      n34[boundaryfunc3]:::RETURN
      n35[x]:::FUNC_PARAM
      n36[y]:::FUNC_PARAM
      n37[z]:::FUNC_PARAM
      n38["`**call** boundaryfunc3`"]:::EXTERNAL
      n39[boundaries entrypoint]:::BOUNDARIES
      n40[result]:::VARIABLE
      n35[x]:::FUNC_PARAM --> n38["`**call** boundaryfunc3`"]:::EXTERNAL
      n36[y]:::FUNC_PARAM --> n38["`**call** boundaryfunc3`"]:::EXTERNAL
      n37[z]:::FUNC_PARAM --> n38["`**call** boundaryfunc3`"]:::EXTERNAL
      n38["`**call** boundaryfunc3`"]:::EXTERNAL -. order .-> n39[boundaries entrypoint]:::BOUNDARIES
      n38["`**call** boundaryfunc3`"]:::EXTERNAL --> n40[result]:::VARIABLE
      n40[result]:::VARIABLE --> n34[boundaryfunc3]:::RETURN
    end
    subgraph b41["boundaryfunc2"]
      n42[boundaryfunc2]:::RETURN
      n43[x]:::FUNC_PARAM
      n44[y]:::FUNC_PARAM
      n45["`**call** boundaryfunc2`"]:::EXTERNAL
      n46[boundaries entrypoint]:::BOUNDARIES
      n47[result]:::VARIABLE
      n43[x]:::FUNC_PARAM --> n45["`**call** boundaryfunc2`"]:::EXTERNAL
      n44[y]:::FUNC_PARAM --> n45["`**call** boundaryfunc2`"]:::EXTERNAL
      n45["`**call** boundaryfunc2`"]:::EXTERNAL -. order .-> n46[boundaries entrypoint]:::BOUNDARIES
      n45["`**call** boundaryfunc2`"]:::EXTERNAL --> n47[result]:::VARIABLE
      n47[result]:::VARIABLE --> n42[boundaryfunc2]:::RETURN
    end
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  linkStyle 4 stroke:#00897b,color:#00897b
  linkStyle 5 stroke:#00897b,color:#00897b
  linkStyle 19 stroke:#c62828,color:#c62828
  linkStyle 20 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 21 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 23 stroke:#2e7d32,color:#2e7d32
  linkStyle 27 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 28 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 30 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 31 stroke:#2e7d32,color:#2e7d32
  linkStyle 32 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 33 stroke:#c62828,color:#c62828
  linkStyle 35 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 36 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 41 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 47 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 53 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 58 stroke:#7b1fa2,color:#7b1fa2
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
  classDef EVENT fill:#00897B40
  classDef BOUNDARIES fill:#7B1FA240
```
