# twoMains: spike value graph

```mermaid
flowchart TD
  n0["Alpha:5 CALL<br/>int fromAlpha = 1"]
  n1["Alpha:5 IDENTIFIER<br/>fromAlpha"]
  n2["Alpha:5 LITERAL<br/>1"]
  n3["Alpha:6 CALL<br/>fromAlpha + 1"]
  n4["Alpha:6 CALL<br/>int alphaOut = fromAlpha + 1"]
  n5["Alpha:6 IDENTIFIER<br/>alphaOut"]
  n6["Alpha:6 IDENTIFIER<br/>fromAlpha"]
  n7["Alpha:6 LITERAL<br/>1"]
  n8["Zeta:5 CALL<br/>int fromZeta = 2"]
  n9["Zeta:5 IDENTIFIER<br/>fromZeta"]
  n10["Zeta:5 LITERAL<br/>2"]
  n11["Zeta:6 CALL<br/>fromZeta + 1"]
  n12["Zeta:6 CALL<br/>int zetaOut = fromZeta + 1"]
  n13["Zeta:6 IDENTIFIER<br/>fromZeta"]
  n14["Zeta:6 IDENTIFIER<br/>zetaOut"]
  n15["Zeta:6 LITERAL<br/>1"]
  n0 -->|"local: ="| n1
  n1 -->|"local: fromAlpha"| n6
  n10 -->|"local: 2"| n8
  n11 -->|"local: fromZeta + 1"| n12
  n12 -->|"local: ="| n14
  n13 -->|"local: fromZeta"| n11
  n15 -->|"local: 1"| n11
  n2 -->|"local: 1"| n0
  n3 -->|"local: fromAlpha + 1"| n4
  n4 -->|"local: ="| n5
  n6 -->|"local: fromAlpha"| n3
  n7 -->|"local: 1"| n3
  n8 -->|"local: ="| n9
  n9 -->|"local: fromZeta"| n13
```
