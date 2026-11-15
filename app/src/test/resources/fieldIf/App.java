/*
 * Fields written on the two sides of a branch, then read below it.
 *
 * A local gets a phi at every join. A field is state on an object, so nothing in the method says
 * whether `this.x` and `other.x` are the same place - the builder holds it, and drawn straight
 * through both arms ran in sequence and the last write won: `a` in `pick` was a dead end and only
 * `b` arrived at `x`. The literals are all different so that a test can ask where each one goes.
 */
package fieldIf;

public class App {
    int x;
    int y;
    int z;
    int err;
    int quiet;
    int w;
    int q;

    /** Both arms write: each argument reaches the read below the `if`. */
    int pick(boolean pickFlag, int a, int b) {
        if (pickFlag) {
            this.x = a;
        } else {
            this.x = b;
        }
        return this.x;
    }

    /** One arm writes, no `else`: the value from before the `if` arrives down the false side. */
    int keep(boolean keepFlag) {
        this.y = 111;
        if (keepFlag) {
            this.y = 122;
        }
        return this.y;
    }

    /** An `if` inside an arm is joined first, and that join is one of the two paths of the outer. */
    int nested(boolean outer, boolean inner) {
        if (outer) {
            if (inner) {
                this.z = 131;
            } else {
                this.z = 132;
            }
        } else {
            this.z = 133;
        }
        return this.z;
    }

    /** An arm that returns still wrote its field, and the caller sees it. */
    void guarded(boolean bad) {
        if (bad) {
            this.err = 141;
            return;
        } else {
            this.err = 142;
        }
    }

    /** A returning arm with nothing on the other side: only the guard's write exists. */
    void failFast(boolean bad) {
        if (bad) {
            this.quiet = 151;
            return;
        }
    }

    void setW(int v) {
        this.w = v;
    }

    /** The write is in a callee, called from each arm: both are still joined in the caller. */
    int viaCallee(boolean calleeFlag) {
        if (calleeFlag) {
            setW(161);
        } else {
            setW(162);
        }
        return this.w;
    }

    void setQ(boolean innerFlag) {
        if (innerFlag) {
            this.q = 171;
        } else {
            this.q = 172;
        }
    }

    /** A callee that forks inside an arm that forks. */
    int calleeThatForks(boolean outerFlag, boolean innerFlag) {
        if (outerFlag) {
            setQ(innerFlag);
        } else {
            this.q = 173;
        }
        return this.q;
    }

    public static void main(String[] args) {
        App app = new App();
        int picked = app.pick(args.length > 0, 101, 102);
        int kept = app.keep(args.length > 1);
        int nestedSeen = app.nested(args.length > 2, args.length > 3);
        app.guarded(args.length > 4);
        int errSeen = app.err;
        app.failFast(args.length > 5);
        int quietSeen = app.quiet;
        int calleeSeen = app.viaCallee(args.length > 6);
        int forkedSeen = app.calleeThatForks(args.length > 7, args.length > 8);
    }
}
