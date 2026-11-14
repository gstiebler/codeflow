# unmodelledReceiver: spike value graph

```mermaid
flowchart TD
  n0["App:8 METHOD_PARAMETER_IN<br/>String[] args"]
  n1["App:9 CALL<br/>(Object) args"]
  n2["App:9 CALL<br/>(Object) args.toString()"]
  n3["App:9 CALL<br/>String out = ((Object) args).toString()"]
  n4["App:9 IDENTIFIER<br/>args"]
  n5["App:9 IDENTIFIER<br/>out"]
  n6["App:9 TYPE_REF<br/>Object"]
  n0 -->|"local: args"| n4
  n1 -->|"local: (Object) args"| n2
  n2 -->|"local: toString()"| n3
  n3 -->|"local: ="| n5
  n4 -->|"local: args"| n1
  n6 -->|"local: Object"| n1
```
