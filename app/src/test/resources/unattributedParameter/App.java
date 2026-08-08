/*
 * A parameter read in a class javac could not attribute, because its supertype is not in the corpus.
 *
 * `Report` extends something one module away, so attribution gives up on its body - but not on its
 * signature. The parameter's *declaration* still resolves, and the *read* of it does not, so the
 * lowering recorded the definition under a PARAMETER element and looked it up under a null one: two
 * keys for one variable in one method, and the read failed the whole corpus. Every exception class in
 * a codebase whose base exception lives elsewhere has this shape, which is how it was found - on
 * Fineract, where `super(reason.errorCode(), ..., glAccountId)` in an exception constructor was
 * enough to produce zero bytes of output for the whole module.
 *
 * The parameter has to be read as a *receiver* and its type declared in the same failing class: a
 * plain `int` parameter of the same constructor resolves on both sides and does not reproduce this at
 * all, which is worth knowing before simplifying the fixture.
 */
package unattributedParameter;

public class App {
    public static void main(String[] args) {
        Long id = 5L;
        Report report = new Report(Report.Reason.LOGGED, id);
    }
}
