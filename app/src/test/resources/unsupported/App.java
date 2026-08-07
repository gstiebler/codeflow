/*
 * A construct codeflow does not model: a cast.
 *
 * The point of this fixture is the failure. Silently scanning through an unmodelled expression
 * and returning one of its children produces a graph that reads fine and is wrong, which is
 * worse than no graph at all when the whole purpose is to be believed about unfamiliar code.
 * So this has to throw, and the message has to say which file and line to look at.
 */
package test;

public class App {
    public static void main(String[] args) {
        System.out.println("x");
        int count = (int) 3L;
    }
}
