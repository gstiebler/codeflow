# unsupportedAssignment: spike value graph

```mermaid
flowchart TD
  n0["App:13:29 METHOD_PARAMETER_IN<br/>String[] args"]
  n1["App:14:16 CALL<br/>Object cast = (Object) args"]
  n2["App:14:16 IDENTIFIER<br/>cast"]
  n3["App:14:23 CALL<br/>(Object) args"]
  n4["App:14:23 TYPE_REF<br/>Object"]
  n5["App:14:32 IDENTIFIER<br/>args"]
  n6["App:15:13 CALL<br/>int size = cast.hashCode()"]
  n7["App:15:13 IDENTIFIER<br/>size"]
  n8["App:15:20 CALL<br/>hashCode()"]
  n9["App:15:20 IDENTIFIER<br/>cast"]
  n0 -->|"local: args"| n5
  n1 -->|"local: ="| n2
  n2 -->|"local: cast"| n9
  n3 -->|"local: (Object) args"| n1
  n4 -->|"local: Object"| n3
  n5 -->|"local: args"| n3
  n6 -->|"local: ="| n7
  n8 -->|"local: hashCode()"| n6
  n9 -->|"local: cast"| n8
```
