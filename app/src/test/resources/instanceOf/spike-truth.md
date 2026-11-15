# instanceOf: spike value graph (condensed)

```mermaid
flowchart TD
  subgraph m0["main"]
    n0["&&"]:::BIN_OP
    n1["cast"]:::BIN_OP
    n2["instanceOf"]:::BIN_OP
    n3["instanceOf"]:::BIN_OP
    n4["length"]:::EXTERNAL
    n5["args"]:::FUNC_PARAM
    n6["text"]:::OBJ_VARIABLE
    n7["value"]:::OBJ_VARIABLE
    n8["isText"]:::VARIABLE
    n9["size"]:::VARIABLE
  end
  n1 --> n6
  n2 --> n8
  n3 --> n0
  n4 --> n9
  n5 --> n7
  n6 --> n4
  n7 --> n1
  n7 --> n2
  n7 --> n3
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
```
