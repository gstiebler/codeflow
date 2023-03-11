```mermaid
flowchart TD
  subgraph methodA
    13[a] --> 16[+]
    14[b] --> 16[+]
    16[+] --> 15[c]
  end
  subgraph main
    10[5] --> 9[x]
  end
```