/*
 * Calls into a boundary class: `--boundary Boundary` says its methods are not to be read. Each call
 * is drawn as the assumed implementation - every parameter into one `call <name>` operation, that into
 * a local `result`, `result` returned - so nothing of Boundary.java's bodies may appear.
 */
package boundary;

public class App {
    static void entrypoint(int a, int b, int c) {
        int result1 = Boundary.boundaryfunc1(a);
        int result2 = 0;
        if (a > b) {
            result2 = Boundary.boundaryfunc2(result1, a);
            Boundary.boundaryfunc3(result2, b, c);
        } else {
            Boundary.boundaryfunc3(a, b, c);
            Boundary.boundaryfunc2(result2, a);
        }
    }
}
