/*
 * A `switch` over an enum declared outside the analysed sources, which took the whole run down.
 *
 * javac can only work out which constant `case UP` names once it knows the selector's type, so when
 * the enum is not in the directory the label resolves to nothing - and a bare identifier that
 * resolved to nothing is what the lowering fails on, correctly, because for a local it means a
 * definition was lost. A case label is not a local. It names a constant, and one javac could not
 * find is one from outside these sources.
 *
 * This is not an exotic input: it is what any module that switches on a type from a neighbouring
 * module looks like when codeflow is pointed at the module alone, which is the normal way to point
 * it at anything. Found on Fineract, where `switch (...getInterestMethod())` over an enum one module
 * away produced zero bytes of output for the whole corpus.
 *
 * Both spellings are here because they share `caseLabel`: the statement form is the one that failed,
 * and the expression form would have failed the same way.
 */
package externalEnumSwitch;

public class App {
    public static void main(String[] args) {
        int fromStatement = 0;
        switch (Config.rounding()) {
            case UP:
                fromStatement = 1;
                break;
            default:
                fromStatement = 2;
        }
        int fromExpression = switch (Config.rounding()) {
            case DOWN -> 3;
            default -> 4;
        };
        int out = fromStatement + fromExpression;
    }
}
