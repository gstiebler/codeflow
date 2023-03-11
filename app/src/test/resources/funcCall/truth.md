```mermaid
flowchart TD
  subgraph methodA
5[a] --> 8[+]
6[b] --> 8[+]
8[+] --> 7[c]
end
subgraph main
2[5] --> 1[x]
end
```