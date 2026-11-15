/*
 * Fields written on the way through a `try`, in its handler, then read below.
 *
 * A handler runs because the `try` did not finish, so both reach the line after them; and a throw can
 * land in the handler from anywhere inside the `try`, so what was there before it is among what the
 * handler starts from. Lowered in sequence the handler's write was the only one left standing.
 */
package fieldTry;

public class App {
    int parsed;
    int before;
    int clean;

    /** The value from the `try` and the fallback from the handler both reach the read. */
    void both(String text) {
        try {
            this.parsed = Integer.parseInt(text);
        } catch (NumberFormatException failure) {
            this.parsed = 301;
        }
    }

    /** The handler writes nothing: what was there before the `try` still arrives. */
    void quiet(String text) {
        this.before = 311;
        try {
            this.before = Integer.parseInt(text);
        } catch (NumberFormatException failure) {
            text.length();
        }
    }

    /** A `try` with no handler is one path: there is nothing to join. */
    void finallyOnly() {
        try {
            this.clean = 321;
        } finally {
            this.clean = this.clean + 1;
        }
    }

    public static void main(String[] args) {
        App app = new App();
        app.both(args[0]);
        int parsedSeen = app.parsed;
        app.quiet(args[0]);
        int beforeSeen = app.before;
        app.finallyOnly();
        int cleanSeen = app.clean;
    }
}
