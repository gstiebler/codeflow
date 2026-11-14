# statementLambda: spike value graph

```mermaid
flowchart TD
  n0["App:14 CALL<br/>IntUnaryOperator twice = value -#gt; { return value + value; }"]
  n1["App:14 IDENTIFIER<br/>twice"]
  n2["App:14 METHOD_PARAMETER_IN<br/>ANY value"]
  n3["App:14 METHOD_REF<br/>statementLambda.App.#lt;lambda#gt;0:#lt;unresolvedSignature#gt;(1)"]
  n4["App:15 CALL<br/>value + value"]
  n5["App:15 IDENTIFIER<br/>value"]
  n6["App:15 RETURN<br/>return value + value;"]
  n7["App:17 CALL<br/>applyAsInt(4)"]
  n8["App:17 CALL<br/>int out = twice.applyAsInt(4)"]
  n9["App:17 IDENTIFIER<br/>out"]
  n10["App:17 IDENTIFIER<br/>twice"]
  n11["App:17 LITERAL<br/>4"]
  n0 -->|"local: ="| n1
  n1 -->|"arg: callback arg 1 value"| n2
  n1 -->|"arg: callback arg"| n3
  n1 -->|"local: twice"| n10
  n10 -->|"local: twice"| n7
  n11 -->|"local: 4"| n7
  n2 -->|"local: value"| n5
  n3 -->|"local: statementLambda.App.#lt;lambda#gt;0:#lt;unresolvedSignature#gt;(1)"| n0
  n4 -->|"local: value + value"| n6
  n5 -->|"local: value"| n4
  n6 -->|"ret: return of #lt;lambda#gt;0()"| n0
  n7 -->|"local: applyAsInt()"| n8
  n8 -->|"local: ="| n9
```
