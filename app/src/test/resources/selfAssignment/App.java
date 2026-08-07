/*
 * `x = x + 1` reads x before it writes it, so the value on the right is the one x held before the
 * statement, not the one the statement produces. Both spellings of it are here because the
 * compound form was right and the expanded form was not, which is not a difference the language
 * makes.
 */
package test;

class Counter {
    int count;
}

public class App {
    public static void main(String[] args) {
        int total = 1;
        total = total + 10;
        final int expanded = total;

        int folded = 2;
        folded += 20;
        final int compound = folded;

        final Counter counter = new Counter();
        counter.count = 3;
        counter.count = counter.count + 30;
        final int field = counter.count;
    }
}
