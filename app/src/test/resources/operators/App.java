/*
 * Every binary operator that shows up in ordinary arithmetic code. Only PLUS, DIVIDE, EQUAL_TO
 * and LESS_THAN used to have a label; everything else rendered as "UNKNOWN", which makes the
 * graph of any real calculation unreadable.
 */
package test;

public class App {
    public static void main(String[] args) {
        final int a = 10;
        final int b = 3;

        final int sum = a + b;
        final int difference = a - b;
        final int product = a * b;
        final int quotient = a / b;
        final int remainder = a % b;

        final boolean equal = a == b;
        final boolean notEqual = a != b;
        final boolean less = a < b;
        final boolean greater = a > b;
        final boolean lessOrEqual = a <= b;
        final boolean greaterOrEqual = a >= b;

        final boolean both = equal && less;
        final boolean either = equal || less;
    }
}
