/*
 * A cast in the one position that used to swallow what it produced: the receiver of a call
 * outside the analysed sources. Found when casts were unmodelled; the operand must still reach the call.
 */
package unmodelledReceiver;

public class App {
    public static void main(String[] args) {
        String out = ((Object) args).toString();
    }
}
