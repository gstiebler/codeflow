```mermaid
flowchart TD
  subgraph main
    1[a] --> 3[b]
    2[5] --> 1[a]
    3[b] --> 6[+]
    3[b] --> 7[d]
    5[8] --> 6[+]
    6[+] --> 4[c]
  end
```