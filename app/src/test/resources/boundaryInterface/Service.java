/*
 * Two roots (`--from Service#run --from Service#known`) calling the boundary interface Store.
 * `run` calls it through a field set from a constructor parameter, whose objects say nothing about
 * their class; `known` through a local that is a `new StoreImpl()`, which dispatch could follow.
 */
package boundaryInterface;

public class Service {
    private final Store store;

    Service(Store store) {
        this.store = store;
    }

    int run(int a) {
        store.save(a);
        return store.load();
    }

    static int known(int b) {
        Store local = new StoreImpl();
        local.save(b);
        return local.load();
    }
}
