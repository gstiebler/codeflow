/*
 * A caught exception, which the handler goes on to read.
 */
package catchParameter;

public class App {
    public static void main(String[] args) {
        int out = 0;
        try {
            out = Integer.parseInt(args[0]);
        } catch (NumberFormatException failure) {
            String reason = failure.getMessage();
            out = reason.length();
        }
    }
}
