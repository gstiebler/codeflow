/*
 * Fields written in the arms of a `switch`, then read below it.
 *
 * Every arm is lowered from the state before the `switch`, and the ones that end at the bottom are
 * joined there. With no `default` the state from above arrives unchanged as well; `case 2:` below a
 * `case 1:` with no `break` is reached both directly and by falling out of it.
 */
package fieldSwitch;

public class App {
    int open;
    int closed;
    int through;
    int other;
    int chosen;

    /** No `default`: whatever `open` held before the `switch` can still be there after it. */
    void noDefault(int k) {
        this.open = 201;
        switch (k) {
            case 1:
                this.open = 202;
                break;
            case 2:
                this.open = 203;
                break;
        }
    }

    /** A `default` catches everything, so the 211 is overwritten on every path. */
    void withDefault(int k) {
        this.closed = 211;
        switch (k) {
            case 1:
                this.closed = 212;
                break;
            default:
                this.closed = 213;
        }
    }

    /** `case 2:` is reached directly (through still 221) and out of `case 1:` (222). */
    void fall(int k) {
        this.through = 221;
        switch (k) {
            case 1:
                this.through = 222;
            case 2:
                this.other = 223;
                break;
            default:
                this.other = 224;
        }
    }

    /** The statement form of the arrow `switch`: no fall-through to allow for. */
    void arrows(int k) {
        switch (k) {
            case 1 -> this.chosen = 231;
            case 2 -> this.chosen = 232;
            default -> this.chosen = 233;
        }
    }

    int expression(int k) {
        return switch (k) {
            case 1 -> {
                this.chosen = 241;
                yield 1;
            }
            default -> {
                this.chosen = 242;
                yield 2;
            }
        };
    }

    public static void main(String[] args) {
        App app = new App();
        app.noDefault(args.length);
        int openSeen = app.open;
        app.withDefault(args.length);
        int closedSeen = app.closed;
        app.fall(args.length);
        int throughSeen = app.through;
        int otherSeen = app.other;
        app.arrows(args.length);
        int arrowSeen = app.chosen;
        int result = app.expression(args.length);
        int expressionSeen = app.chosen;
    }
}
