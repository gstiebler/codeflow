# unary: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["!"]:::BIN_OP
    n1["++"]:::BIN_OP
    n2["-"]:::BIN_OP
    n3["0"]:::LITERAL
    n4["7"]:::LITERAL
    n5["true"]:::LITERAL
    n6["afterIncrement"]:::VARIABLE
    n7["counter"]:::VARIABLE
    n8["flag"]:::VARIABLE
    n9["inverted"]:::VARIABLE
    n10["negated"]:::VARIABLE
    n11["value"]:::VARIABLE
  end
  n0 --> n9
  n11 --> n2
  n2 --> n10
  n3 --> n7
  n4 --> n11
  n5 --> n8
  n7 --> n1
  n7 --> n6
  n8 --> n0
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
