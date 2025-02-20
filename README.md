[![Java CI with Gradle](https://github.com/gstiebler/codeflow/actions/workflows/gradle.yml/badge.svg)](https://github.com/gstiebler/codeflow/actions/workflows/gradle.yml)

# codeflow
Generates a dataflow representation from Java code.

For example, converts the following Java code
```java

abstract class BaseClass {
    int baseMember;

    public BaseClass(int init, int baseParam) {
        this.baseMember = init;
    }

    public abstract int process(int baseParam);
}

class ClassA extends BaseClass {
    int aMember;

    public ClassA() {
        super(10, 55);
    }

    @Override
    public int process(int baseParam) {
        return baseMember + baseParam;
    }
}

class ClassB extends BaseClass {
    int bMember;

    public ClassB() {
        super(20, 55);
        this.bMember = 30;
    }

    @Override
    public int process(int baseParam) {
        return baseMember * bMember;
    }
}

public class Main {
    public static void main(String[] args) {
        BaseClass bcPointer;
        bcPointer = new ClassA();
        int a = bcPointer.process(40);
        System.out.println("Result from ClassA: " + a);
    }
}

```

into
```mermaid
flowchart TD
  subgraph sub1["main"]
    40[40]:::LITERAL --> baseParam[baseParam]:::FUNC_PARAM
    subgraph sub2["ClassA.constructor"]
        10[10]:::LITERAL --> init[init]:::FUNC_PARAM
        55[55]:::LITERAL --> baseParam2[baseParam]:::FUNC_PARAM
        subgraph sub3["BaseClass.constructor"]
            init[init]:::FUNC_PARAM --> bm[this.baseMember]:::VARIABLE
            baseParam2[baseParam]:::FUNC_PARAM
        end
    end
    subgraph sub4["ClassA.process"]
        baseParam[baseParam]:::FUNC_PARAM --> binop1["#43;"]:::BIN_OP
        bm[this.baseMember]:::VARIABLE --> binop1["#43;"]:::BIN_OP
        binop1["#43;"]:::BIN_OP --> return1["ClassA.process"]:::RETURN
    end
    a:::VARIABLE
    return1["return"]:::RETURN --> a
  end
  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8000FF30
  classDef RETURN fill:#FF808080
```


## Build and run tests
```shell
./gradlew build
```

## Run the application
```shell
./gradlew run --args="path/to/java/dir"
```
