/*
 * A chain of calls, where the receiver of one call is the result of another. Real code reaches the
 * standard library this way constantly: `adjustmentTransactions.stream().sorted(...).toList()` in
 * Fineract's CapitalizedIncomeAmortizationUtil is the case that turned this up.
 *
 * The receiver of `.toList()` is not a name, it is another invocation, so anything that assumes a
 * call's receiver is a plain identifier falls over here.
 */
package test;

import java.util.List;

public class App {
    public static void main(String[] args) {
        final List<String> items = List.of();
        final int size = items.stream().toList().size();
    }
}
