/*
 * A local read where codeflow has no node for it: a pattern on a `switch` used as a statement
 * binds a name, and only the expression form of `switch` is modelled, so nothing declares `text`.
 *
 * The point of this fixture is the failure. A field with no value is the program - it holds its
 * default - but a local cannot be read before it is written, so a local with no value means the
 * analysis lost it, and that has to be said rather than drawn as a value from nowhere.
 */
package unboundLocal;

public class App {
    public static void main(String[] args) {
        Object value = args;
        switch (value) {
            case String text -> System.out.println(text);
            default -> System.out.println("none");
        }
    }
}
