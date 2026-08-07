/*
 * The same unmodelled construct as the `unsupported` fixture, but assigned to an object.
 *
 * That path used to catch every exception from evaluating the right-hand side and carry on, so
 * the variable was drawn with nothing flowing into it. On a diagram a value arriving from nowhere
 * is indistinguishable from one that genuinely has no source, which is the silent wrongness the
 * whole gate exists to prevent - and object assignment is most of real Java.
 */
package unsupportedAssignment;

public class App {
    public static void main(String[] args) {
        Object cast = (Object) args;
        int size = cast.hashCode();
    }
}
