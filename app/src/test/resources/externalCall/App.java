/*
 * Calls to methods that are not part of the analysed sources. There is no body to inline, so the
 * call becomes one opaque node: the receiver and the arguments flow in, the result flows out.
 *
 * Throwing instead is what stops the tool being pointed at real code, where almost every method
 * touches the standard library.
 */
package test;

public class App {
    public static void main(String[] args) {
        final int a = 5;
        final int b = Math.abs(a);
        System.out.println(b);
        final int c = b + 1;
    }
}
