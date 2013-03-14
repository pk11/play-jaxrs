package controllers;

import play.*;
import play.mvc.*;

import views.html.*;
import com.google.common.base.Optional;
import javax.ws.rs.*;

@Path("/")
public class Application extends Controller {

    @GET
    public static Result index() {
        return ok(index.render("Your new application is ready."));
    }

    @GET
    @Path("/id/{id}")
    @Produces("text/html")
    @With(VerboseAction.class)
    public static Result id(@PathParam("id") String id, @QueryParam("foo") Optional<String> foo) {
        return ok("id=" + id + " query=" + foo.or("booo")+" delegate="+session().get("delegate"));
    }

    public static class VerboseAction extends Action.Simple {
        public Result call(Http.Context ctx) throws Throwable {
            Logger.info("Calling action for " + ctx);
            ctx.session().put("delegate","yay");
            return delegate.call(ctx);
        }
    }
}
