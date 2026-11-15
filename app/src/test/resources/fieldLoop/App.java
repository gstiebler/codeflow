/*
 * Fields written in a loop body, then read after the loop.
 *
 * A loop can run zero times, so the value from before it reaches the line below as well as the one
 * the body wrote. Drawn straight through, the body ran once and was the only write left.
 */
package fieldLoop;

public class App {
    int total;
    int last;
    int each;
    int again;

    void counted(int n) {
        this.total = 401;
        for (int i = 0; i < n; i++) {
            this.total = 402;
        }
    }

    void whileLoop(int n) {
        this.last = 411;
        while (n > 0) {
            this.last = 412;
            n--;
        }
    }

    void enhanced(int[] items) {
        this.each = 421;
        for (int item : items) {
            this.each = 422;
        }
    }

    /** A `do` runs once at least, but is joined like the others: one value too many, none too few. */
    void doWhile(int n) {
        this.again = 431;
        do {
            this.again = 432;
            n--;
        } while (n > 0);
    }

    public static void main(String[] args) {
        App app = new App();
        app.counted(args.length);
        int totalSeen = app.total;
        app.whileLoop(args.length);
        int lastSeen = app.last;
        app.enhanced(new int[] {1, 2});
        int eachSeen = app.each;
        app.doWhile(args.length);
        int againSeen = app.again;
    }
}
