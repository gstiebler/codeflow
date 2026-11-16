/*
 * The one implementation. Its bodies must never reach the graph when Store is a boundary, not even
 * where the receiver is known to be a `new StoreImpl()`: each writes a literal nothing else in the
 * fixture does, so a test can tell whether a body was read.
 */
package boundaryInterface;

public class StoreImpl implements Store {
    private int kept;

    public void save(int x) {
        kept = x * 7001;
    }

    public int load() {
        return kept + 7002;
    }
}
