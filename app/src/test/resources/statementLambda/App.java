/*
 * A lambda with a statement body.
 *
 * Its body is walked in the enclosing method's block, so its `return` has to be redirected to the
 * lambda: wired to the block it is being walked in, it would say `main` returns a value it does
 * not.
 */
package statementLambda;

import java.util.function.IntUnaryOperator;

public class App {
    public static void main(String[] args) {
        IntUnaryOperator twice = value -> {
            return value + value;
        };
        int out = twice.applyAsInt(4);
    }
}
