package unattributedParameter;

// `outside.Base` is deliberately not in this directory: that is what stops javac attributing the
// body below, and the point of the fixture.
public class Report extends outside.Base {

    public enum Reason {
        LOGGED;

        public String code() {
            return name();
        }
    }

    public Report(final Reason reason, final Long id) {
        super(reason.code(), id);
    }
}
