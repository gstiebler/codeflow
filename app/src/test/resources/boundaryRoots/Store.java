/*
 * The bodies here must never reach the graph when Store is a boundary: each returns a literal
 * nothing else in the fixture writes, so a test can tell whether a body was read.
 */
package boundaryRoots;

public class Store {
    static int save(int x) {
        return 8001;
    }

    static int load(int x) {
        return x + 8002;
    }
}
