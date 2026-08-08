/*
 * A constant imported statically from outside the analysed sources.
 *
 * `import static` is how most Java spells a shared constant, and pointing codeflow at one module of
 * a multi-module build is the normal way to point it at anything - so the declaration being absent
 * is the common case, not an exotic one. javac says it could not find the name by handing back a
 * `ClassSymbol`: kind CLASS, where a variable was asked for. Believed, that reached the lowering's
 * local-resolution path, which fails - correctly, for a local, since a local read before it is
 * written means a definition was lost. One such import therefore produced zero bytes of output for
 * the entire corpus. Found on Fineract, in a module whose constants live one module away.
 *
 * The read is used twice so the assertion is about the value flowing on, not just about a box
 * existing somewhere.
 */
package externalConstant;

import static outside.Limits.MAX_RETRIES;

public class App {
    public static void main(String[] args) {
        int budget = MAX_RETRIES;
        int spent = budget + 1;
    }
}
