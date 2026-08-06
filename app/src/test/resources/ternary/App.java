/*
 * A guarded division, the shape Fineract uses to protect a divisor:
 *   daysUntilMaturity == 0 ? BigDecimal.ZERO : amount.multiply(days).divide(daysUntilMaturity)
 *
 * Both branches produce the value of the expression, and the condition decides which one, so all
 * three have to reach the result. Leaving the conditional to TreeScanner's default handling wires
 * up only one branch and drops the other, along with the guard itself.
 */
package test;

public class App {
    public static void main(String[] args) {
        final int divisor = 0;
        final int value = 100;
        final int fallback = 7;
        final int guarded = divisor == 0 ? fallback : value / divisor;
    }
}
