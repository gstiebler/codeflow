/*
 * The bodies here must never reach the graph when Boundary is a boundary: each returns a literal
 * nothing else in the fixture writes, so a test can tell whether a body was read.
 */
package boundary;

public class Boundary {
    static int boundaryfunc1(int x) {
        return 9001;
    }

    static int boundaryfunc2(int x, int y) {
        return x * 9002 + y;
    }

    static int boundaryfunc3(int x, int y, int z) {
        return 9003;
    }
}
