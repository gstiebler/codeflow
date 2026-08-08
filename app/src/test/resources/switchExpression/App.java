/*
 * `switch` used as an expression, which had no fixture at all and four bugs to show for it.
 *
 * The arms are deliberately of all four shapes Java allows, because each was wrong in its own way:
 *
 *   - an expression body, the only one that worked;
 *   - a block body, whose statements were never walked and whose `yield` was dropped, so `doubled`
 *     was computed by code the diagram did not contain and the value the switch produced came from
 *     an `UNMODELLED` box instead;
 *   - a `throw`, which produces no value at all and was listed among the values the switch could
 *     evaluate to - the fabricated flow this project exists to prevent, and the one shape almost
 *     every real `switch` expression ends with;
 *   - and the arms were lowered in sequence from one set of definitions, so `case 3` read the `note`
 *     that `case 2` wrote, although exactly one of them runs.
 *
 * `note` is written in one arm and read in a later one for that last reason, and read again below
 * the switch so the join has something to be checked against. The labels are uneven - three cases
 * and a default, one of them with two statements - so an implementation that walks one arm, or all
 * of them in sequence, fails rather than looking plausible.
 */
package switchExpression;

public class App {
    public static void main(String[] args) {
        int kind = args.length;
        int base = 7;
        int bonus = 11;
        int fallback = 13;
        int note = 0;

        int total = switch (kind) {
            case 1 -> base;
            case 2 -> {
                note = 41;
                int doubled = bonus * 2;
                yield doubled;
            }
            case 3 -> note - fallback;
            default -> throw new IllegalStateException("unsupported");
        };

        int out = total + note;
    }
}
