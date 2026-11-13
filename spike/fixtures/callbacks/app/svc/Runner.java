package app.svc;

public class Runner {

    // Callee is in source; only the dispatch cb.call(v) -> the caller's lambda is unknown.
    public void run(String v, Callback cb) {
        cb.call(v);
    }
}
