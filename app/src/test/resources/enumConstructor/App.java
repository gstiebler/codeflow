/*
 * Enum constants with constructor arguments, ported from codemap's `enum` fixture, whose constants
 * carry initializers.
 *
 * `enumConstant` next door covers the bare form, where the declaration is the whole value and there
 * is nothing behind it. Here there is: `SMALL(3)` runs a constructor that fills a field, and a
 * getter reads it back. The enum constructor never ran, so `Size.SMALL` was an opaque node and
 * `units()` returned a field with nothing flowing into it - a getter drawn as producing a value
 * out of nowhere, on an enum shaped like most enums in real code.
 *
 * The two constants are two objects, which is why both are read: one memory position shared
 * between them would give whichever ran last to both callers.
 */
package enumConstructor;

enum Size {
    SMALL(3),
    LARGE(9);

    private final int units;

    Size(int units) {
        this.units = units;
    }

    int units() {
        return units;
    }
}

public class App {
    public static void main(String[] args) {
        int small = Size.SMALL.units();
        int large = Size.LARGE.units();
        int out = small + large;
    }
}
