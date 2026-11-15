/*
 * A boundary in the fixture's tests: none of these bodies is read when Gateway is one.
 */
package boundaryOrder;

public class Gateway {
    static int open(int key) {
        return 7001;
    }

    static int fetch(int key) {
        return 7002;
    }

    static void log(int value) {
    }

    static void close(int value) {
    }
}
