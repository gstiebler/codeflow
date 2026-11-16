/*
 * The boundary: under `--boundary Port` none of these bodies is read. Each returns a literal nothing
 * else in the fixture writes, so a test can tell whether a body was read.
 */
package boundaryBranch;

public class Port {
    static void open(int x) {
    }

    static int send(int x) {
        return 7001;
    }

    static int flush(int x) {
        return 7002;
    }

    static void close(int x) {
    }
}
