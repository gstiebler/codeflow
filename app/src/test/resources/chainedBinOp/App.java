/*
 * A chain of the same operator. The outer and the inner `+` share a start position, so an id
 * derived from the start position alone gives both the same id: the two operations collapse
 * into one node that has an edge to itself.
 */
package test;

public class App {
    public static void main(String[] args) {
        final int a = 10;
        final int b = 2;
        final int c = 3;
        final int chained = a + b + c;
        final int nested = a - b - c;
    }
}
