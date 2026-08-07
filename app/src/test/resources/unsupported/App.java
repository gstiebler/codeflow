/*
 * A construct codeflow does not model: a cast.
 *
 * It is drawn rather than thrown on. Silently scanning through an unmodelled expression and
 * returning one of its children produces a graph that reads fine and is wrong, which is worse than
 * no graph at all when the whole purpose is to be believed about unfamiliar code - so the gap gets
 * a node of its own, saying which construct and which line. Failing outright was the other
 * extreme: one cast on a reachable path cost the reader every other file in the corpus.
 */
package test;

public class App {
    public static void main(String[] args) {
        System.out.println("x");
        int count = (int) 3L;
    }
}
