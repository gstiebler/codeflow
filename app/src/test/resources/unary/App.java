/*
 * Unary operators, which used to vanish from the graph entirely.
 *
 * TreeScanner's default returns the operand's own node, so `-value` was indistinguishable from
 * `value` and `!flag` from `flag`: the diagram showed a value being used exactly where its
 * negation was, with nothing to indicate anything had been dropped.
 */
package test;

public class App {
    public static void main(String[] args) {
        final int value = 7;
        final boolean flag = true;

        final int negated = -value;
        final boolean inverted = !flag;

        int counter = 0;
        counter++;
        final int afterIncrement = counter;
    }
}
