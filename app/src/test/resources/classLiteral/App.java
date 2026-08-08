/*
 * `boolean.class`, which is a type name where a value would go.
 *
 * A class literal's receiver names a type, and the check for that asks javac what the tree resolved
 * to - but a *primitive* type name has no `Element` to answer with, so `boolean` fell through to
 * being evaluated as a value. A type produces none, so the run died. `enum JavaType {
 * BOOLEAN(boolean.class), ... }` is the shape it was found in, and a table mapping primitives to
 * their wrappers is ordinary code.
 *
 * The reference-type literal is here beside it because it took the other path all along, and the two
 * have to come out the same: they are the same expression with a different type named in it.
 */
package classLiteral;

public class App {
    public static void main(String[] args) {
        Class<?> primitive = boolean.class;
        Class<?> reference = String.class;
        register(primitive, reference);
    }

    private static void register(Class<?> first, Class<?> second) {
        String pair = first.getName() + second.getName();
    }
}
