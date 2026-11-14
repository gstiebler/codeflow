# localClass: spike value graph

```mermaid
flowchart TD
  n0["App:12:13 CALL<br/>int seed = 3"]
  n1["App:12:13 IDENTIFIER<br/>seed"]
  n2["App:12:20 LITERAL<br/>3"]
  n3["App:13:9 METHOD_PARAMETER_IN<br/>this"]
  n4["App:14:23 METHOD_PARAMETER_IN<br/>int n"]
  n5["App:15:17 RETURN<br/>return n * 2;"]
  n6["App:15:24 CALL<br/>n * 2"]
  n7["App:15:24 IDENTIFIER<br/>n"]
  n8["App:15:28 LITERAL<br/>2"]
  n9["App:18 CALL<br/>new Doubler()"]
  n10["App:18:13 CALL<br/>int result = new Doubler().twice(seed)"]
  n11["App:18:13 IDENTIFIER<br/>result"]
  n12["App:18:22 CALL<br/>$obj0 = new Doubler()"]
  n13["App:18:22 CALL<br/>new Doubler()"]
  n14["App:18:22 CALL<br/>new Doubler().twice(seed)"]
  n15["App:18:22 IDENTIFIER #2<br/>$obj0"]
  n16["App:18:22 IDENTIFIER<br/>$obj0"]
  n17["App:18:42 IDENTIFIER<br/>seed"]
  n18["App:19:16 FIELD_IDENTIFIER<br/>out"]
  n19["App:19:28 IDENTIFIER<br/>result"]
  n20["App:19:9 CALL<br/>System.out"]
  n21["App:19:9 CALL<br/>System.out.println(result)"]
  n22["App:19:9 IDENTIFIER<br/>System"]
  n0 -->|"local: ="| n1
  n1 -->|"local: seed"| n17
  n10 -->|"local: ="| n11
  n11 -->|"local: result"| n19
  n12 -->|"local: ="| n16
  n13 -->|"local: new Doubler()"| n12
  n14 -->|"local: twice()"| n10
  n15 -->|"arg: arg 0 this"| n3
  n15 -->|"local: $obj0"| n9
  n16 -->|"local: $obj0"| n15
  n17 -->|"arg: arg 1 n"| n4
  n18 -->|"local: out"| n20
  n19 -->|"local: result"| n21
  n2 -->|"local: 3"| n0
  n20 -->|"local: field ANY.out"| n21
  n22 -->|"local: System"| n20
  n4 -->|"local: n"| n7
  n5 -->|"ret: return of twice()"| n14
  n6 -->|"local: n * 2"| n5
  n7 -->|"local: n"| n6
  n8 -->|"local: 2"| n6
```
