/*
 * An object that comes from outside the analysed sources. There is no constructor to run, so the
 * object gets a memory position of its own that nothing is known about, and calls on it are
 * external calls like any other.
 *
 * Demanding a known memory position for every non-primitive is what stops the tool at the first
 * `List.of()` or `LocalDate.now()` in real code.
 */
package test;

import java.util.List;

public class App {
    public static void main(String[] args) {
        final List<String> items = List.of();
        final int size = items.size();
        final int doubled = size + size;
    }
}
