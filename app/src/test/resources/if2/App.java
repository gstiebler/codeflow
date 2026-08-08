/*
 * A guarded `return` is a choice, and the guard is what makes it.
 *
 * `if (v > 100) { return r; }` and the `return s` below it are two values the method can produce,
 * and which one it does is decided by `v > 100`. Drawn as two plain arrows into the result the
 * diagram says only "one of these" - and the comparison that chose, having nothing below the `if`
 * to gate, is left on the page with no edge out of it at all. That is the same wound the join for a
 * variable had before it was gated.
 *
 * The outer `if` is here so the two shapes sit side by side: its branches both fall out of the
 * bottom, so `r` joins below it in the ordinary way, while the inner one leaves the method.
 */
package test;

public class App {
    static int classify(int v, int limit) {
        int r = 0;
        if (v > limit) {
            r = 1;
            if (v > 100) {
                return r;
            }
            r = 2;
        } else {
            if (v < 0) {
                r = 3;
            } else {
                r = 4;
            }
        }
        final int s = r;
        return s;
    }

    /* Both arms leave, so no path falls through and the older exit is what the younger one gates. */
    static int either(int v) {
        if (v > 0) {
            return 6;
        } else {
            return 7;
        }
    }

    public static void main(String[] args) {
        final int a = 3;
        final int b = classify(a, 10);
        final int c = b;
        final int d = either(a);
    }
}
