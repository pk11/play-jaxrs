import play.*;
import play.mvc.*;
import play.mvc.Http.*;

public class Global extends GlobalSettings {

  @Override
  public void onStart(Application app) {
    Logger.info("Application has started");
  } 
  @Override 
  public play.api.mvc.Handler onRouteRequest(RequestHeader request) {
     return org.pk11.jaxrs.Router.handlerFor(this, request);
  }
  @Override
  public void onStop(Application app) {
    Logger.info("Application shutdown...");
  } 

    
}