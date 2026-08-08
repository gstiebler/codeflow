/**
 * A class whose supertype is not in the corpus, reading a field it inherits from it.
 *
 * `outside.Base` does not exist here, so javac attributes this class's signature and cannot work out
 * what it inherits. It says so the way it says everything: `this.code` comes back as an `Element` of
 * kind CLASS, named `Report.code`, typed ERROR - a member it could not find, reported in the same
 * shape as a *name* it could not find. Believed at face value that is a read of a field nothing
 * declares, and the run died with "'code' has no value in describe".
 *
 * Which is a limit of the *sources*: the field is real, it is declared one module away, and there is
 * nothing about it codeflow could draw. `EXTERNAL` is what that says.
 *
 * Pointing codeflow at one module of a multi-module build is the normal way to point it at anything,
 * and a domain class extending a base class in another module is ordinary Java, so this shape is
 * common rather than exotic - it is `this.currency` in Fineract's `RecurringDepositAccount`, whose
 * `SavingsAccount` lives in `fineract-savings`. Keep the `extends` and keep the base class absent:
 * with a supertype it can see, javac resolves the field and this fixture asserts nothing.
 */
public class Report extends outside.Base {

    /**
     * Two reads of the same inherited field, because the receiver decides what flows in.
     *
     * `this` produces no value, so the opaque node `this.code` becomes has no input. `other` is a
     * value somebody wrote, so `other.code` takes it: the object a field was read from is the only
     * thing on the page that says where the value came from, and dropping it would leave the local
     * reaching nothing.
     */
    public String describe(final Report other) {
        return this.code + other.code;
    }
}
