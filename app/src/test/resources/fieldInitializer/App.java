/*
 * Field initializers and instance initializer blocks, ported from codemap's `constructor_chain`,
 * whose C++ member-init lists are Java field initializers.
 *
 * Nothing outside a method body was ever walked, so every one of these was skipped: `counted` was
 * read by the constructor with nothing flowing into it, `blocked` was a field nobody had assigned,
 * and `nested` named an object that had never been constructed, so the chain through it died.
 *
 * `Plain` declares no constructor on purpose. There is no body to inline there and no box to draw,
 * but `seeded = 4` is still code somebody wrote and still runs on every `new Plain()`.
 */
package fieldInitializer;

class Inner {
    int held;

    Inner(int seed) {
        held = seed + 1;
    }
}

class Plain {
    int seeded = 4;
}

class Outer {
    Inner nested = new Inner(10);
    Plain plain = new Plain();
    int counted = 5;
    int blocked;

    {
        blocked = 7;
    }

    Outer(int add) {
        counted = counted + add;
    }

    // Delegating with `this(...)` is the one case where the initializers do not run again: the
    // constructor delegated to has already run them, and running them at both ends of the chain
    // draws every one of them twice.
    Outer() {
        this(1);
    }
}

public class App {
    public static void main(String[] args) {
        Outer outer = new Outer(3);
        int fromField = outer.counted;
        int fromBlock = outer.blocked;
        int fromNested = outer.nested.held;
        int fromPlain = outer.plain.seeded;

        Outer delegated = new Outer();
        int fromDelegated = delegated.counted;
    }
}
