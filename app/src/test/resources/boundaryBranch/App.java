/*
 * The order token at an `if`: under `--boundary Port` each `if` whose arms call into Port splits the
 * token on its condition - one `if` box taking the token and the condition, `true` into the then-arm's
 * first Port call and `false` into the else-arm's. Each root is one case; `entrypoint` runs them all,
 * one after another, so one graph holds every case. Every condition has its own operator, so a test
 * can tell which `if` split the token.
 */
package boundaryBranch;

public class App {
    static void entrypoint(int a, int b, int c) {
        noElse(a, b);
        onlyElse(a, b);
        nested(a, b, c);
        untouched(a, b);
    }

    /** No `else`: the split's `false` goes straight to the token's join. */
    static void noElse(int a, int b) {
        Port.open(a);
        if (a > b) {
            Port.send(b);
        }
        Port.close(a);
    }

    /** A Port call only in the `else`: the split's `true` goes straight to the token's join. */
    static void onlyElse(int a, int b) {
        Port.open(a);
        if (a < b) {
            b = b + 1;
        } else {
            Port.send(b);
        }
        Port.close(b);
    }

    /** An `if` first in the then-arm of another: two splits, each on its own condition. */
    static void nested(int a, int b, int c) {
        Port.open(a);
        if (a >= b) {
            if (b <= c) {
                Port.send(c);
            } else {
                Port.flush(c);
            }
            Port.send(a);
        } else {
            Port.flush(b);
        }
        Port.close(c);
    }

    /** No Port call in either arm: the token passes the `if` by, and nothing splits it. */
    static int untouched(int a, int b) {
        Port.open(a);
        int r;
        if (a == b) {
            r = a;
        } else {
            r = b;
        }
        Port.close(r);
        return r;
    }
}
