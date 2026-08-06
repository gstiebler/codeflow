/*
 * A type that is nowhere in the analysed sources and nowhere on the classpath, which is the normal
 * case for the code this tool is pointed at: attribution runs with no classpath at all.
 *
 * javac marks what it could not resolve rather than guessing, and codeflow only believes a symbol
 * of the kind it asked for, so these calls take the same opaque path as any other external call.
 * The value still has to stay traceable across them, and the run still has to produce a graph.
 */
package test;

import com.example.missing.Widget;

public class App {
    public static void main(String[] args) {
        int size = 5;
        Widget widget = Widget.create(size);
        int measured = widget.measure();
        int total = measured + 1;
    }
}
