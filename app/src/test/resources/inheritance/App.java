/*
 * A field is declared once, on whichever class in the chain declares it, and every subclass reads
 * the same one. Nothing here is written by the class it is read through.
 */
package test;

class Base {
    int fromBase;
}

class Middle extends Base {
    int fromMiddle;
}

class Leaf extends Middle {
    int fromLeaf;
}

public class App {
    public static void main(String[] args) {
        final Leaf leaf = new Leaf();
        leaf.fromBase = 5;
        leaf.fromMiddle = 10;
        leaf.fromLeaf = 20;

        final int total = leaf.fromBase + leaf.fromMiddle + leaf.fromLeaf;
    }
}
