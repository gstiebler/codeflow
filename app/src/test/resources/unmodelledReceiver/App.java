/*
 * An unmodelled expression in the one position that used to swallow it: the receiver of a call
 * outside the analysed sources.
 */
package unmodelledReceiver;

public class App {
    public static void main(String[] args) {
        String out = ((Object) args).toString();
    }
}
