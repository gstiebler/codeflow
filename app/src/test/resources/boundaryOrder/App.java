/*
 * The order boundary calls run in, across a method that is inlined: `--boundary Gateway` threads one
 * `boundaries` token through every Gateway call, so open -> fetch -> log -> close is one chain even
 * though fetch and log are called from `helper`. `log` and `close` are void and thread it too.
 *
 * `boundaries` below is the program's own field and shares only the token's name: it must stay an
 * ordinary static field, with no edge to or from the chain.
 */
package boundaryOrder;

public class App {
    static int boundaries;

    static void entrypoint(int a, int b) {
        int opened = Gateway.open(a);
        int fetched = helper(b);
        boundaries = a;
        int seen = boundaries;
        Gateway.close(fetched + seen + opened);
    }

    static int helper(int x) {
        int y = Gateway.fetch(x);
        Gateway.log(y);
        return y;
    }
}
