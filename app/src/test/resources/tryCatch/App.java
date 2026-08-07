/*
 * A variable written on the way through a `try` and again in its handler, then read below both.
 *
 * A handler runs because the `try` did *not* finish, so the two are alternatives and both reach
 * the line after them - which makes this the same join as an `if`, arrived at from a different
 * direction. Lowered in sequence the handler's value is the only one left, and the diagram says
 * `result` can only be the fallback: the success path, which is the one the code is written for,
 * is missing.
 *
 * The handler also has to see what was in scope *before* the `try`, since a throw can land there
 * from anywhere inside it - `attempts` is read in the handler and written in neither.
 */
package tryCatch;

public class App {
    public static void main(String[] args) {
        int attempts = 1;
        int result;
        try {
            result = Integer.parseInt(args[0]);
        } catch (NumberFormatException failure) {
            result = attempts;
        }
        int reported = result;
    }
}
