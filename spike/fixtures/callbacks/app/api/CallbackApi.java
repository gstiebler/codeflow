package app.api;

import app.repo.ItemRepository;
import app.svc.Runner;
import java.util.List;
import java.util.Optional;

@Path("/cb")
public class CallbackApi {

    private ItemRepository repo;

    private Runner runner;

    // a: lambda CAPTURES the parameter (invoked by JDK code).
    @POST
    @Path("captured")
    public void captured(String param) {
        Optional.<String>empty().orElseGet(() -> repo.save(param));
    }

    // b: value reaches the lambda through its own PARAMETER, invoked by JDK code.
    @POST
    @Path("lambda-param")
    public void lambdaParam(String param) {
        List.of(param).forEach(x -> repo.save(x));
    }

    // c: value comes back out of a lambda's RETURN, then is saved at the call site.
    @POST
    @Path("lambda-return")
    public void lambdaReturn(String param) {
        String s = Optional.of(param).map(v -> v + "!").orElse("");
        repo.save(s);
    }

    // d: METHOD REFERENCE as the callback.
    @POST
    @Path("method-ref")
    public void methodRef(String param) {
        List.of(param).forEach(repo::save);
    }

    // e: OWN callback interface; callee run() is in source, only the dispatch to the lambda is unknown.
    @POST
    @Path("own-callback")
    public void ownCallback(String param) {
        runner.run(param, v -> repo.save(v));
    }

    // f: CONTROL, direct call. Must be found.
    @POST
    @Path("direct")
    public void direct(String param) {
        repo.save(param);
    }
}
