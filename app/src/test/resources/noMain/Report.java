/*
 * A class with no `main`, which is what most Java is.
 *
 * Services, controllers and libraries have no entry point of their own, so a tool that can only
 * start from `main` excludes most of its own subject matter - and did so by refusing to produce
 * anything at all. Pointed at this with `--from`, it graphs the method it was asked for.
 */
package noMain;

public class Report {

    public int total(int base) {
        int bonus = base * 2;
        return base + bonus;
    }
}
