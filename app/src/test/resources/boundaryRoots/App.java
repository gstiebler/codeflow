/*
 * Two entry points (`--from App#first --from App#second`) into one boundary class (`--boundary
 * Store`). Each root draws its own order chain, `boundaries first` and `boundaries second`, and its
 * own `call <name>` box per call site; `first` calls two boundary methods, so its order is visible.
 * `later.call()` is an outside call that is not a boundary: it is labelled `call` and is not bold.
 */
package boundaryRoots;

import java.util.concurrent.Callable;

public class App {
    static int first(int a) {
        int saved = Store.save(a);
        return Store.load(saved);
    }

    static int second(int b, Callable<Integer> later) throws Exception {
        return Store.save(b) + later.call();
    }
}
