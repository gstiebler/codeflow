/*
 * A local read before anything writes it, which the compiler rejects and codeflow still meets.
 *
 * Attribution runs on input that does not compile - that is the property the whole approach rests
 * on, since a tool pointed at a directory nobody has read cannot require it to build - so a corpus
 * carrying an error like this one is walked like any other, and the read arrives with nothing to
 * find.
 *
 * The point of the fixture is the failure, and it is the same point `unboundLocal` used to make: a
 * field with no value is the program, since a field holds its default, but a local with no value is
 * not something to draw. Guessing a value from nowhere would make this diagram indistinguishable
 * from a correct one.
 */
package unwrittenLocal;

public class App {
    public static void main(String[] args) {
        int total;
        System.out.println(total);
    }
}
