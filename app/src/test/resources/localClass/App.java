/*
 * A class declared inside a method body.
 *
 * The declaration runs nothing: `twice` runs when something calls it, which is a call site like
 * any other. Walking into the body at the point of declaration would put `n * 2` in `main`'s own
 * dataflow, which is a flow the program does not have.
 */
package localClass;

public class App {
    public static void main(String[] args) {
        int seed = 3;
        class Doubler {
            int twice(int n) {
                return n * 2;
            }
        }
        int result = new Doubler().twice(seed);
        System.out.println(result);
    }
}
