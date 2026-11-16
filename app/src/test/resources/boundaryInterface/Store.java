/*
 * The boundary (`--boundary Store`): an interface, so neither method has a body. A service reaches
 * it through an injected, interface-typed field, the way a Spring service reaches its gateways.
 */
package boundaryInterface;

public interface Store {
    void save(int x);

    int load();
}
