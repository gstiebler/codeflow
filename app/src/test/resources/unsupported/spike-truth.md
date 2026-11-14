# unsupported: spike value graph

```mermaid
flowchart TD
  n0["App:14:16 FIELD_IDENTIFIER<br/>out"]
  n1["App:14:28 LITERAL<br/>#quot;x#quot;"]
  n2["App:14:9 CALL<br/>System.out"]
  n3["App:14:9 CALL<br/>System.out.println(#quot;x#quot;)"]
  n4["App:14:9 IDENTIFIER<br/>System"]
  n5["App:15:13 CALL<br/>int count = (int) 3L"]
  n6["App:15:13 IDENTIFIER<br/>count"]
  n7["App:15:21 CALL<br/>(int) 3L"]
  n8["App:15:21 TYPE_REF<br/>int"]
  n9["App:15:27 LITERAL<br/>3L"]
  n0 -->|"local: out"| n2
  n1 -->|"local: #quot;x#quot;"| n3
  n2 -->|"local: field ANY.out"| n3
  n4 -->|"local: System"| n2
  n5 -->|"local: ="| n6
  n7 -->|"local: (int) 3L"| n5
  n8 -->|"local: int"| n7
  n9 -->|"local: 3L"| n7
```
