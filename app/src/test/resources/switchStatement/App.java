/*
 * `switch` used as a statement, ported from codemap's `switch` fixture.
 *
 * The selector is compared against each case label, so the read of `selector` is what decides
 * which arm runs and has to be visible on the diagram. The arms are deliberately uneven - one
 * falls through, one writes a second variable, the default writes both - so that an implementation
 * that walked only the first arm, or only the last, fails rather than looking plausible.
 */
package switchStatement;

public class App {
    public static void main(String[] args) {
        int selector = 3;
        int chosen = 0;
        int side = 200;
        switch (selector) {
            case 1:
                chosen = 10;
                break;
            case 2:
                chosen = 20;
                side = 300;
            case 3:
                chosen = 30;
                break;
            default:
                chosen = 100;
                side = 500;
        }
        int out = chosen + side;
    }
}
