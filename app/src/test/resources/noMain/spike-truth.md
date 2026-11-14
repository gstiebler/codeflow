# noMain: spike value graph

```mermaid
flowchart TD
  n0["Report:12 METHOD_PARAMETER_IN<br/>int base"]
  n1["Report:13 CALL<br/>base * 2"]
  n2["Report:13 CALL<br/>int bonus = base * 2"]
  n3["Report:13 IDENTIFIER<br/>base"]
  n4["Report:13 IDENTIFIER<br/>bonus"]
  n5["Report:13 LITERAL<br/>2"]
  n6["Report:14 CALL<br/>base + bonus"]
  n7["Report:14 IDENTIFIER<br/>base"]
  n8["Report:14 IDENTIFIER<br/>bonus"]
  n9["Report:14 RETURN<br/>return base + bonus;"]
  n0 -->|"local: base"| n3
  n1 -->|"local: base * 2"| n2
  n2 -->|"local: ="| n4
  n3 -->|"local: base"| n1
  n3 -->|"local: base"| n7
  n4 -->|"local: bonus"| n8
  n5 -->|"local: 2"| n1
  n6 -->|"local: base + bonus"| n9
  n7 -->|"local: base"| n6
  n8 -->|"local: bonus"| n6
```
