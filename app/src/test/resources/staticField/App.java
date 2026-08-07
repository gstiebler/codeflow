/*
 * A static field belongs to the class, not to any instance, and `Counter.total` and a bare `total`
 * inside Counter are the same variable. There is no object to hang it on, so the memory position
 * that carries an instance field is not available and the block-parent chain is the wrong shape:
 * `bump` is a sibling of nothing, and its write has to be found by a read in `main` afterwards.
 */
package test;

class Counter {
    static int total;

    static void bump(int by) {
        total = total + by;
    }
}

public class App {
    public static void main(String[] args) {
        Counter.total = 5;
        final int before = Counter.total;

        Counter.bump(3);
        final int after = Counter.total;
    }
}
