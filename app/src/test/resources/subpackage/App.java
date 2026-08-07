/*
 * The callee is in another package, in a subdirectory. Resolution is by element, so where the file
 * sits decides nothing.
 */
package subpackage;

import subpackage.util.Adder;

public class App {
    public static void main(String[] args) {
        final Adder adder = new Adder();
        final int sum = adder.add(3, 5);
    }
}
