/*
 * A cast on the right-hand side of an object assignment.
 *
 * That path used to catch every exception from evaluating the right-hand side and carry on, so
 * the variable was drawn with nothing flowing into it. On a diagram a value arriving from nowhere
 * is indistinguishable from one that genuinely has no source - and object assignment is most of
 * real Java. It was found with a cast when casts were unmodelled; now that they are modelled the
 * same position must still hand the operand on to the variable.
 */
package unsupportedAssignment;

public class App {
    public static void main(String[] args) {
        Object cast = (Object) args;
        int size = cast.hashCode();
    }
}
