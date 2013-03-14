`play-jaxrs` is providing an alternative router for play(2.1) java apps by implementing (a subset of) the [JAX-RS](http://jax-rs-spec.java.net/) specification on top of play. 

tl;dr
=====

use this router instead of the built-in one if you are interested in a native java solution that also significantly improves build times. The price you will need to pay for this is lack of reverse routing and compile time routing checks.  


play-jaxrs tradeoffs
====================


- using reflection for action dispatching instead of matching generated route rules   


- since there are no router files to compile and sbt can not invalidate big parts of the object graph, compile times are significantly better than the standard solution


- only supports java projects for two reasons: 
  - annotations are primary used in java 
  - scala users already have a very scala-centric routing solution

- route definitions are provided inline. Some people prefer this over an external DSL, especially in a backend service-only context


- reflection based dispatching means no reverse routing or compile time checks for route matching 


what's supported?
=================

- `@GET`,`@POST`, `@PUT`, `@DELETE`, `@OPTIONS`

- `@ApplicationPath` on your `Global` class can define a context (think servlet context)

- `@Path` works both on classes and methods

- URI parts can be captured (i.e. `/user/id/{id}`) 

- the captured field can only be a String i.e.  

```
    @GET
    @Path("/id/{id}")
    public static Result id(@PathParam("id") String id) {
        ...        
    }
```

- `@QueryParam` captured as `com.google.common.base.Optional` i.e.

```
@GET
@Path("/id/{id}")
public static Result id(@PathParam("id") String id, @QueryParam("foo") Optional<String> foo) {
    return ok("id=" + id + " query=" + foo.or("booo"));
}
```
- `@Provides`
- `@Consumes`

plus all the standard play features should be working (i.e. action composition, assets serving etc.).


Curious?
=========
- check out the [examples] (https://github.com/pk11/play-jaxrs/blob/master/app/controllers/Application.java)/[specs] (https://github.com/pk11/play-jaxrs/tree/master/test)


How to install
=====================================

you can install `play-jaxrs` in three easy steps:

1) delete `conf/routes`

2) in ```project/Build.scala``` add
 
 ```"org.pk11" %% "jax-rs" % "0.5"``` 

to your ```project/Build.scala``` file's ```app dependencies``` section.


3) also 

 ```resolvers += "pk11" at "http://pk11-scratch.googlecode.com/svn/trunk"``` 

should be part of your settings

4) finally, set it up in your ```Global``` class:
```
  @Override 
  public play.api.mvc.Handler onRouteRequest(RequestHeader request) {
     return org.pk11.jaxrs.Router.handlerFor(this, request);
  }
```  

and that's it.


How to hack
===========

after cloning and cd-ing into the main directory

run the spec
===========

```$ play test```


package
=======

```$ play -Djaxrs=only publish-local```



License
========

Published under The MIT License, see LICENSE
