/*
 * One reference variable holding either of two objects, then a field read through it.
 *
 * After the `if` the name `chosen` stands for two possible objects, so `chosen.amount` is either
 * field, and both have to be on the page. Tracking one object per variable can only pick an arm -
 * and picking one draws a graph that is complete, readable, and about a run the program might never
 * make.
 *
 * The two are constructed with different literals so that which one survives is visible: a diagram
 * showing only `11` reaching `total` is the `if` walked into one branch, wearing an alias instead of
 * a phi.
 */
package aliasBranch;

public class App {
    static class Holder {
        int amount;

        Holder(int amount) {
            this.amount = amount;
        }
    }

    public static void main(String[] args) {
        Holder chosen;
        if (args.length > 0) {
            chosen = new Holder(11);
        } else {
            chosen = new Holder(22);
        }
        int total = chosen.amount;
    }
}
