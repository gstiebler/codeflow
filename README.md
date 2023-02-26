# codeflow
Generates a dataflow representation from Java code.

For example, converts the following Java code
```java
public class App {
    public static void main(String[] args) {
        final int a = 5;
        final int b = a;
        final int c = b + 8;
        System.out.println(b);
    }
}
```

into
```mermaid
flowchart TD
    4744[a] --> 4745[b]
    4745[b] --> 65488937[+]
    28597262[5] --> 4744[a]
    33233312[8] --> 65488937[+]
    65488937[+] --> 4746[c]
```