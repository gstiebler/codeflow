/*
 * A caller and a callee on the same object, each declaring a local named `amount`.
 *
 * Keyed by name and owning instance the two were one variable, and both were filed on the same
 * MemPos under the same key, so the callee's overwrote the caller's. Reading `amount` back in
 * outer() then found the value inner() had put there: the graph showed 2 reaching `afterwards`,
 * which is a flow the code cannot produce.
 */
package test;

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.outer();
    }

    private void outer() {
        int amount = 1;
        this.inner();
        int afterwards = amount;
    }

    private void inner() {
        int amount = 2;
        int consumed = amount;
    }
}
