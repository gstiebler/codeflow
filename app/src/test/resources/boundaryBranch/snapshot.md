```mermaid
flowchart TD
  subgraph b0["entrypoint"]
    n1[event entrypoint]:::EVENT
    n2[entrypoint]:::RETURN
    n3[a]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM
    n5[c]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n2[entrypoint]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n3[a]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n4[b]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n5[c]:::FUNC_PARAM
    n1[event entrypoint]:::EVENT -. event .-> n7[noElse]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n11[open]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n13[boundaries entrypoint]:::BOUNDARIES
    n1[event entrypoint]:::EVENT -. event .-> n27[close]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n32[onlyElse]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n41[1]:::LITERAL
    n1[event entrypoint]:::EVENT -. event .-> n36[open]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n56[close]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n61[nested]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n66[open]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n103[close]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n112[open]:::RETURN
    n1[event entrypoint]:::EVENT -. event .-> n122[close]:::RETURN
    n3[a]:::FUNC_PARAM --> n8[a]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n33[a]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n62[a]:::FUNC_PARAM
    n3[a]:::FUNC_PARAM --> n109[a]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n9[b]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n34[b]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n63[b]:::FUNC_PARAM
    n4[b]:::FUNC_PARAM --> n110[b]:::FUNC_PARAM
    n5[c]:::FUNC_PARAM --> n64[c]:::FUNC_PARAM
    n30[boundaries entrypoint]:::BOUNDARIES -. order .-> n38["`**call** open`"]:::EXTERNAL
    n59[boundaries entrypoint]:::BOUNDARIES -. order .-> n68["`**call** open`"]:::EXTERNAL
    n106[boundaries entrypoint]:::BOUNDARIES -. order .-> n114["`**call** open`"]:::EXTERNAL
    subgraph b6["noElse"]
      n7[noElse]:::RETURN
      n8[a]:::FUNC_PARAM
      n9[b]:::FUNC_PARAM
      n16[>]:::BIN_OP
      n20[if]:::BIN_OP
      n24[if]:::BIN_OP
      n25[boundaries entrypoint]:::BOUNDARIES
      n8[a]:::FUNC_PARAM --> n12[x]:::FUNC_PARAM
      n8[a]:::FUNC_PARAM --> n16[>]:::BIN_OP
      n8[a]:::FUNC_PARAM --> n28[x]:::FUNC_PARAM
      n9[b]:::FUNC_PARAM --> n16[>]:::BIN_OP
      n9[b]:::FUNC_PARAM --> n19[x]:::FUNC_PARAM
      n16[>]:::BIN_OP -->|if| n20[if]:::BIN_OP
      n16[>]:::BIN_OP -->|if| n24[if]:::BIN_OP
      n20[if]:::BIN_OP -->|true| n21["`**call** send`"]:::EXTERNAL
      n20[if]:::BIN_OP -->|false| n24[if]:::BIN_OP
      n24[if]:::BIN_OP --> n25[boundaries entrypoint]:::BOUNDARIES
      n25[boundaries entrypoint]:::BOUNDARIES -. order .-> n29["`**call** close`"]:::EXTERNAL
      n15[boundaries entrypoint]:::BOUNDARIES -. order .-> n20[if]:::BIN_OP
      n22[boundaries entrypoint]:::BOUNDARIES -->|true| n24[if]:::BIN_OP
      subgraph b10["open"]
        n11[open]:::RETURN
        n12[x]:::FUNC_PARAM
        n13[boundaries entrypoint]:::BOUNDARIES
        n14["`**call** open`"]:::EXTERNAL
        n15[boundaries entrypoint]:::BOUNDARIES
        n12[x]:::FUNC_PARAM --> n14["`**call** open`"]:::EXTERNAL
        n13[boundaries entrypoint]:::BOUNDARIES -. order .-> n14["`**call** open`"]:::EXTERNAL
        n14["`**call** open`"]:::EXTERNAL -. order .-> n15[boundaries entrypoint]:::BOUNDARIES
      end
      subgraph b17["send"]
        n18[send]:::RETURN
        n19[x]:::FUNC_PARAM
        n21["`**call** send`"]:::EXTERNAL
        n22[boundaries entrypoint]:::BOUNDARIES
        n23[result]:::VARIABLE
        n19[x]:::FUNC_PARAM --> n21["`**call** send`"]:::EXTERNAL
        n21["`**call** send`"]:::EXTERNAL -. order .-> n22[boundaries entrypoint]:::BOUNDARIES
        n21["`**call** send`"]:::EXTERNAL --> n23[result]:::VARIABLE
        n23[result]:::VARIABLE --> n18[send]:::RETURN
      end
      subgraph b26["close"]
        n27[close]:::RETURN
        n28[x]:::FUNC_PARAM
        n29["`**call** close`"]:::EXTERNAL
        n30[boundaries entrypoint]:::BOUNDARIES
        n28[x]:::FUNC_PARAM --> n29["`**call** close`"]:::EXTERNAL
        n29["`**call** close`"]:::EXTERNAL -. order .-> n30[boundaries entrypoint]:::BOUNDARIES
      end
    end
    subgraph b31["onlyElse"]
      n32[onlyElse]:::RETURN
      n33[a]:::FUNC_PARAM
      n34[b]:::FUNC_PARAM
      n40[<]:::BIN_OP
      n41[1]:::LITERAL
      n42[+]:::BIN_OP
      n43[b]:::VARIABLE
      n47[if]:::BIN_OP
      n51[if]:::BIN_OP
      n52[b]:::VARIABLE
      n53[if]:::BIN_OP
      n54[boundaries entrypoint]:::BOUNDARIES
      n33[a]:::FUNC_PARAM --> n37[x]:::FUNC_PARAM
      n33[a]:::FUNC_PARAM --> n40[<]:::BIN_OP
      n34[b]:::FUNC_PARAM --> n40[<]:::BIN_OP
      n34[b]:::FUNC_PARAM --> n42[+]:::BIN_OP
      n34[b]:::FUNC_PARAM --> n46[x]:::FUNC_PARAM
      n34[b]:::FUNC_PARAM -->|false| n51[if]:::BIN_OP
      n40[<]:::BIN_OP -->|if| n47[if]:::BIN_OP
      n40[<]:::BIN_OP -->|if| n51[if]:::BIN_OP
      n40[<]:::BIN_OP -->|if| n53[if]:::BIN_OP
      n41[1]:::LITERAL --> n42[+]:::BIN_OP
      n42[+]:::BIN_OP --> n43[b]:::VARIABLE
      n43[b]:::VARIABLE -->|true| n51[if]:::BIN_OP
      n47[if]:::BIN_OP -->|false| n48["`**call** send`"]:::EXTERNAL
      n47[if]:::BIN_OP -->|true| n53[if]:::BIN_OP
      n51[if]:::BIN_OP --> n52[b]:::VARIABLE
      n52[b]:::VARIABLE --> n57[x]:::FUNC_PARAM
      n53[if]:::BIN_OP --> n54[boundaries entrypoint]:::BOUNDARIES
      n54[boundaries entrypoint]:::BOUNDARIES -. order .-> n58["`**call** close`"]:::EXTERNAL
      n39[boundaries entrypoint]:::BOUNDARIES -. order .-> n47[if]:::BIN_OP
      n49[boundaries entrypoint]:::BOUNDARIES -->|false| n53[if]:::BIN_OP
      subgraph b35["open"]
        n36[open]:::RETURN
        n37[x]:::FUNC_PARAM
        n38["`**call** open`"]:::EXTERNAL
        n39[boundaries entrypoint]:::BOUNDARIES
        n37[x]:::FUNC_PARAM --> n38["`**call** open`"]:::EXTERNAL
        n38["`**call** open`"]:::EXTERNAL -. order .-> n39[boundaries entrypoint]:::BOUNDARIES
      end
      subgraph b44["send"]
        n45[send]:::RETURN
        n46[x]:::FUNC_PARAM
        n48["`**call** send`"]:::EXTERNAL
        n49[boundaries entrypoint]:::BOUNDARIES
        n50[result]:::VARIABLE
        n46[x]:::FUNC_PARAM --> n48["`**call** send`"]:::EXTERNAL
        n48["`**call** send`"]:::EXTERNAL -. order .-> n49[boundaries entrypoint]:::BOUNDARIES
        n48["`**call** send`"]:::EXTERNAL --> n50[result]:::VARIABLE
        n50[result]:::VARIABLE --> n45[send]:::RETURN
      end
      subgraph b55["close"]
        n56[close]:::RETURN
        n57[x]:::FUNC_PARAM
        n58["`**call** close`"]:::EXTERNAL
        n59[boundaries entrypoint]:::BOUNDARIES
        n57[x]:::FUNC_PARAM --> n58["`**call** close`"]:::EXTERNAL
        n58["`**call** close`"]:::EXTERNAL -. order .-> n59[boundaries entrypoint]:::BOUNDARIES
      end
    end
    subgraph b60["nested"]
      n61[nested]:::RETURN
      n62[a]:::FUNC_PARAM
      n63[b]:::FUNC_PARAM
      n64[c]:::FUNC_PARAM
      n70[>=]:::BIN_OP
      n71[<=]:::BIN_OP
      n75[if]:::BIN_OP
      n76[if]:::BIN_OP
      n86[if]:::BIN_OP
      n87[boundaries entrypoint]:::BOUNDARIES
      n100[if]:::BIN_OP
      n101[boundaries entrypoint]:::BOUNDARIES
      n62[a]:::FUNC_PARAM --> n67[x]:::FUNC_PARAM
      n62[a]:::FUNC_PARAM --> n70[>=]:::BIN_OP
      n62[a]:::FUNC_PARAM --> n90[x]:::FUNC_PARAM
      n63[b]:::FUNC_PARAM --> n70[>=]:::BIN_OP
      n63[b]:::FUNC_PARAM --> n71[<=]:::BIN_OP
      n63[b]:::FUNC_PARAM --> n96[x]:::FUNC_PARAM
      n64[c]:::FUNC_PARAM --> n71[<=]:::BIN_OP
      n64[c]:::FUNC_PARAM --> n74[x]:::FUNC_PARAM
      n64[c]:::FUNC_PARAM --> n82[x]:::FUNC_PARAM
      n64[c]:::FUNC_PARAM --> n104[x]:::FUNC_PARAM
      n70[>=]:::BIN_OP -->|if| n75[if]:::BIN_OP
      n70[>=]:::BIN_OP -->|if| n100[if]:::BIN_OP
      n71[<=]:::BIN_OP -->|if| n76[if]:::BIN_OP
      n71[<=]:::BIN_OP -->|if| n86[if]:::BIN_OP
      n75[if]:::BIN_OP -->|true| n76[if]:::BIN_OP
      n75[if]:::BIN_OP -->|false| n97["`**call** flush`"]:::EXTERNAL
      n76[if]:::BIN_OP -->|true| n77["`**call** send`"]:::EXTERNAL
      n76[if]:::BIN_OP -->|false| n83["`**call** flush`"]:::EXTERNAL
      n86[if]:::BIN_OP --> n87[boundaries entrypoint]:::BOUNDARIES
      n87[boundaries entrypoint]:::BOUNDARIES -. order .-> n91["`**call** send`"]:::EXTERNAL
      n100[if]:::BIN_OP --> n101[boundaries entrypoint]:::BOUNDARIES
      n101[boundaries entrypoint]:::BOUNDARIES -. order .-> n105["`**call** close`"]:::EXTERNAL
      n69[boundaries entrypoint]:::BOUNDARIES -. order .-> n75[if]:::BIN_OP
      n78[boundaries entrypoint]:::BOUNDARIES -->|true| n86[if]:::BIN_OP
      n84[boundaries entrypoint]:::BOUNDARIES -->|false| n86[if]:::BIN_OP
      n92[boundaries entrypoint]:::BOUNDARIES -->|true| n100[if]:::BIN_OP
      n98[boundaries entrypoint]:::BOUNDARIES -->|false| n100[if]:::BIN_OP
      subgraph b65["open"]
        n66[open]:::RETURN
        n67[x]:::FUNC_PARAM
        n68["`**call** open`"]:::EXTERNAL
        n69[boundaries entrypoint]:::BOUNDARIES
        n67[x]:::FUNC_PARAM --> n68["`**call** open`"]:::EXTERNAL
        n68["`**call** open`"]:::EXTERNAL -. order .-> n69[boundaries entrypoint]:::BOUNDARIES
      end
      subgraph b72["send"]
        n73[send]:::RETURN
        n74[x]:::FUNC_PARAM
        n77["`**call** send`"]:::EXTERNAL
        n78[boundaries entrypoint]:::BOUNDARIES
        n79[result]:::VARIABLE
        n74[x]:::FUNC_PARAM --> n77["`**call** send`"]:::EXTERNAL
        n77["`**call** send`"]:::EXTERNAL -. order .-> n78[boundaries entrypoint]:::BOUNDARIES
        n77["`**call** send`"]:::EXTERNAL --> n79[result]:::VARIABLE
        n79[result]:::VARIABLE --> n73[send]:::RETURN
      end
      subgraph b80["flush"]
        n81[flush]:::RETURN
        n82[x]:::FUNC_PARAM
        n83["`**call** flush`"]:::EXTERNAL
        n84[boundaries entrypoint]:::BOUNDARIES
        n85[result]:::VARIABLE
        n82[x]:::FUNC_PARAM --> n83["`**call** flush`"]:::EXTERNAL
        n83["`**call** flush`"]:::EXTERNAL -. order .-> n84[boundaries entrypoint]:::BOUNDARIES
        n83["`**call** flush`"]:::EXTERNAL --> n85[result]:::VARIABLE
        n85[result]:::VARIABLE --> n81[flush]:::RETURN
      end
      subgraph b88["send"]
        n89[send]:::RETURN
        n90[x]:::FUNC_PARAM
        n91["`**call** send`"]:::EXTERNAL
        n92[boundaries entrypoint]:::BOUNDARIES
        n93[result]:::VARIABLE
        n90[x]:::FUNC_PARAM --> n91["`**call** send`"]:::EXTERNAL
        n91["`**call** send`"]:::EXTERNAL -. order .-> n92[boundaries entrypoint]:::BOUNDARIES
        n91["`**call** send`"]:::EXTERNAL --> n93[result]:::VARIABLE
        n93[result]:::VARIABLE --> n89[send]:::RETURN
      end
      subgraph b94["flush"]
        n95[flush]:::RETURN
        n96[x]:::FUNC_PARAM
        n97["`**call** flush`"]:::EXTERNAL
        n98[boundaries entrypoint]:::BOUNDARIES
        n99[result]:::VARIABLE
        n96[x]:::FUNC_PARAM --> n97["`**call** flush`"]:::EXTERNAL
        n97["`**call** flush`"]:::EXTERNAL -. order .-> n98[boundaries entrypoint]:::BOUNDARIES
        n97["`**call** flush`"]:::EXTERNAL --> n99[result]:::VARIABLE
        n99[result]:::VARIABLE --> n95[flush]:::RETURN
      end
      subgraph b102["close"]
        n103[close]:::RETURN
        n104[x]:::FUNC_PARAM
        n105["`**call** close`"]:::EXTERNAL
        n106[boundaries entrypoint]:::BOUNDARIES
        n104[x]:::FUNC_PARAM --> n105["`**call** close`"]:::EXTERNAL
        n105["`**call** close`"]:::EXTERNAL -. order .-> n106[boundaries entrypoint]:::BOUNDARIES
      end
    end
    subgraph b107["untouched"]
      n108[untouched]:::RETURN
      n109[a]:::FUNC_PARAM
      n110[b]:::FUNC_PARAM
      n116[==]:::BIN_OP
      n117[r]:::VARIABLE
      n118[r]:::VARIABLE
      n119[if]:::BIN_OP
      n120[r]:::VARIABLE
      n109[a]:::FUNC_PARAM --> n113[x]:::FUNC_PARAM
      n109[a]:::FUNC_PARAM --> n116[==]:::BIN_OP
      n109[a]:::FUNC_PARAM --> n117[r]:::VARIABLE
      n110[b]:::FUNC_PARAM --> n116[==]:::BIN_OP
      n110[b]:::FUNC_PARAM --> n118[r]:::VARIABLE
      n116[==]:::BIN_OP -->|if| n119[if]:::BIN_OP
      n117[r]:::VARIABLE -->|true| n119[if]:::BIN_OP
      n118[r]:::VARIABLE -->|false| n119[if]:::BIN_OP
      n119[if]:::BIN_OP --> n120[r]:::VARIABLE
      n120[r]:::VARIABLE --> n123[x]:::FUNC_PARAM
      n120[r]:::VARIABLE --> n108[untouched]:::RETURN
      n115[boundaries entrypoint]:::BOUNDARIES -. order .-> n124["`**call** close`"]:::EXTERNAL
      subgraph b111["open"]
        n112[open]:::RETURN
        n113[x]:::FUNC_PARAM
        n114["`**call** open`"]:::EXTERNAL
        n115[boundaries entrypoint]:::BOUNDARIES
        n113[x]:::FUNC_PARAM --> n114["`**call** open`"]:::EXTERNAL
        n114["`**call** open`"]:::EXTERNAL -. order .-> n115[boundaries entrypoint]:::BOUNDARIES
      end
      subgraph b121["close"]
        n122[close]:::RETURN
        n123[x]:::FUNC_PARAM
        n124["`**call** close`"]:::EXTERNAL
        n125[boundaries entrypoint]:::BOUNDARIES
        n123[x]:::FUNC_PARAM --> n124["`**call** close`"]:::EXTERNAL
        n124["`**call** close`"]:::EXTERNAL -. order .-> n125[boundaries entrypoint]:::BOUNDARIES
      end
    end
  end
  linkStyle 0 stroke:#00897b,color:#00897b
  linkStyle 1 stroke:#00897b,color:#00897b
  linkStyle 2 stroke:#00897b,color:#00897b
  linkStyle 3 stroke:#00897b,color:#00897b
  linkStyle 4 stroke:#00897b,color:#00897b
  linkStyle 5 stroke:#00897b,color:#00897b
  linkStyle 6 stroke:#00897b,color:#00897b
  linkStyle 7 stroke:#00897b,color:#00897b
  linkStyle 8 stroke:#00897b,color:#00897b
  linkStyle 9 stroke:#00897b,color:#00897b
  linkStyle 10 stroke:#00897b,color:#00897b
  linkStyle 11 stroke:#00897b,color:#00897b
  linkStyle 12 stroke:#00897b,color:#00897b
  linkStyle 13 stroke:#00897b,color:#00897b
  linkStyle 14 stroke:#00897b,color:#00897b
  linkStyle 15 stroke:#00897b,color:#00897b
  linkStyle 16 stroke:#00897b,color:#00897b
  linkStyle 26 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 27 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 28 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 34 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 35 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 36 stroke:#2e7d32,color:#2e7d32
  linkStyle 37 stroke:#c62828,color:#c62828
  linkStyle 39 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 40 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 41 stroke:#2e7d32,color:#2e7d32
  linkStyle 43 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 44 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 46 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 50 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 56 stroke:#c62828,color:#c62828
  linkStyle 57 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 58 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 59 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 62 stroke:#2e7d32,color:#2e7d32
  linkStyle 63 stroke:#c62828,color:#c62828
  linkStyle 64 stroke:#2e7d32,color:#2e7d32
  linkStyle 68 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 69 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 70 stroke:#c62828,color:#c62828
  linkStyle 72 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 74 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 78 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 89 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 90 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 91 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 92 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 93 stroke:#2e7d32,color:#2e7d32
  linkStyle 94 stroke:#c62828,color:#c62828
  linkStyle 95 stroke:#2e7d32,color:#2e7d32
  linkStyle 96 stroke:#c62828,color:#c62828
  linkStyle 98 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 100 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 101 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 102 stroke:#2e7d32,color:#2e7d32
  linkStyle 103 stroke:#c62828,color:#c62828
  linkStyle 104 stroke:#2e7d32,color:#2e7d32
  linkStyle 105 stroke:#c62828,color:#c62828
  linkStyle 107 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 109 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 113 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 117 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 121 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 125 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 131 stroke:#6a6a6a,color:#6a6a6a
  linkStyle 132 stroke:#2e7d32,color:#2e7d32
  linkStyle 133 stroke:#c62828,color:#c62828
  linkStyle 137 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 139 stroke:#7b1fa2,color:#7b1fa2
  linkStyle 141 stroke:#7b1fa2,color:#7b1fa2
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
