/*
 * The two forms of array creation: sized, and written out element by element.
 */
package arrayCreation;

public class App {
    public static void main(String[] args) {
        int size = 2;
        int[] sized = new int[size];
        int seed = 7;
        int[] filled = new int[] { seed, 9 };
        int out = filled[0] + sized[1];
    }
}
