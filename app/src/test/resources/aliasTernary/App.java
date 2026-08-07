/*
 * A ternary choosing between two objects, then a field read through what it produced.
 *
 * `c ? a : b` is the same choice an `if` makes, written as an expression, so the name it is assigned
 * to points at either object and `chosen.amount` is either field. With the ternary producing no
 * object at all the field is read on a position nothing ever wrote to, and `total` is drawn taking a
 * value that arrives from nowhere - readable, plausible, and missing both of the numbers the program
 * can actually put there.
 *
 * The chooser is not one of the alternatives: `flag` decides which object it is and is not an object
 * itself, which is why the alternatives have to be marked rather than every input unioned. `new
 * Holder[]` is the case that makes that concrete - an array is not the objects it holds.
 */
package aliasTernary;

public class App {
    static class Holder {
        int amount;

        Holder(int amount) {
            this.amount = amount;
        }
    }

    public static void main(String[] args) {
        boolean flag = args.length > 0;
        Holder chosen = flag ? new Holder(11) : new Holder(22);
        int total = chosen.amount;
    }
}
