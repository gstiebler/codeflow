# unwrittenLocal: spike value graph

```mermaid
flowchart TD
  n0["App:19:16 FIELD_IDENTIFIER<br/>out"]
  n1["App:19:28 IDENTIFIER<br/>total"]
  n2["App:19:9 CALL<br/>System.out"]
  n3["App:19:9 CALL<br/>System.out.println(total)"]
  n4["App:19:9 IDENTIFIER<br/>System"]
  n0 -->|"local: out"| n2
  n1 -->|"local: total"| n3
  n2 -->|"local: field ANY.out"| n3
  n4 -->|"local: System"| n2
```
