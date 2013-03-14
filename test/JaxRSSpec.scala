package test

import org.scalatest._
import javax.ws.rs._

import play.api.test._
import play.api.test.Helpers._
import org.scalatest.PrivateMethodTester
import org.pk11.jaxrs
import play.mvc._
import com.google.common.base.Optional
import org.scalatest.matchers.ShouldMatchers
import java.lang.reflect.Method
import play.mvc.Results._
import collection.JavaConverters._
import play.core.j.JavaHelpers

@Path("/working")
class Working {}

@Path("/yay")
class ApplicationPath {}


@Path("work")
class Work extends play.mvc.Controller {

  @GET
  @Path("/boo")
  def boo(): play.mvc.Result = {
     ok("Your new application is ready.")
  }
  
  @GET
  @Path("/id/{id}/name/{name}")
  def index(@PathParam("id") id: String, @PathParam("name") name: String): play.mvc.Result = {
     ok("id="+id+" name="+name)
  }

  @GET
  @Path("/c")
  @Consumes(Array("application/json"))
  def consumes(@PathParam("id") id: String, @PathParam("name") name: String): play.mvc.Result = {
     ok("id="+id+" name="+name)
  }

}

@Path("/test/{id}")
class TestController extends play.mvc.Controller{

  @GET
  def t(@PathParam("id") id:String, @QueryParam("foo") q: Optional[String]): play.mvc.Result = {
     ok("id="+id+" q="+q.get)
  }

}


class JaxRSSpec extends FlatSpec with ShouldMatchers with PrivateMethodTester{

  "A jaxrs.Router" should "identify whether a resource path valid or not" in {
    val isResourcePath = PrivateMethod[Boolean]('isResourcePath)
    jaxrs.Router invokePrivate isResourcePath("/","/") should equal (true)
    jaxrs.Router invokePrivate isResourcePath("/","/path") should equal (false)
    jaxrs.Router invokePrivate isResourcePath("/user/1/name/joe","/user/{id}/name/{name}") should equal (true)
  }

 
  "A jaxrs.Router" should "find longest match" in {
      val findTheLongestMatch = PrivateMethod[Option[(Class[_], String)]]('findLongestMatch)
      val classes = new java.util.HashSet[Class[_]]()
      classes.add(classOf[Working])
      classes.add(classOf[Work])
      classes.add(classOf[TestController])
      classes.add(classOf[ApplicationPath])
      val potentialClass = jaxrs.Router invokePrivate findTheLongestMatch(classes.asScala.toSet,JavaHelpers.createJavaRequest(FakeRequest("GET", "/work")), "") getOrElse(throw new Exception("should match shortest path"))
      potentialClass._1.getName should equal("test.Work")
      potentialClass._2 should equal("/work")
      val potentialClassB = jaxrs.Router invokePrivate findTheLongestMatch(classes.asScala.toSet,JavaHelpers.createJavaRequest(FakeRequest("GET", "/working")), "") getOrElse(throw new Exception("should match shortest path"))
      potentialClassB._1.getName should equal("test.Working")
      potentialClassB._2 should equal("/working")
      val potentialClassC = jaxrs.Router invokePrivate findTheLongestMatch(classes.asScala.toSet,JavaHelpers.createJavaRequest(FakeRequest("GET", "/test/5")), "") getOrElse(throw new Exception("should match shortest path"))
      potentialClassC._1.getName should equal("test.TestController")
      potentialClassC._2 should equal("/test/{id}")  
      jaxrs.Router invokePrivate findTheLongestMatch(classes.asScala.toSet,JavaHelpers.createJavaRequest(FakeRequest("GET", "/test")), "") should equal (None)
      val applicationPathClass = jaxrs.Router invokePrivate findTheLongestMatch(classes.asScala.toSet,JavaHelpers.createJavaRequest(FakeRequest("GET", "/context/yay")), "/context") getOrElse(throw new Exception("should find ApplicationPath"))
      applicationPathClass._1.getName should equal("test.ApplicationPath")

  }

  "A jaxrs.Router" should "find correct method" in {
      val findMethodAndGenerateContext = PrivateMethod[Option[(Method, Map[String, String])]]('findMethodAndGenerateContext)
      val relevantMethods = PrivateMethod[java.util.Set[Method]]('relevantMethods)
      val workMethods: java.util.Set[Method] = jaxrs.Router invokePrivate relevantMethods(classOf[Work],classOf[GET])
      val testCMethods : java.util.Set[Method] = jaxrs.Router invokePrivate relevantMethods(classOf[TestController],classOf[GET])
      val m = jaxrs.Router invokePrivate findMethodAndGenerateContext("/work", "/work/id/1/name/joe",workMethods, "text/html") getOrElse(throw new Exception("should find method"))
      m._2.toString should equal ("Map(id -> 1, name -> joe)")
      m._1.getName should equal ("index")
      val dummyMethod = jaxrs.Router invokePrivate findMethodAndGenerateContext("/work", "/work/boo",workMethods, "text/html") 
      dummyMethod.get._1.getName should equal("boo")
      val w = jaxrs.Router invokePrivate findMethodAndGenerateContext("/work", "/work/c",workMethods, "text/html")
      w.isDefined should equal (false)
      val j = jaxrs.Router invokePrivate findMethodAndGenerateContext("/work", "/work/c",workMethods, "application/json")
      j.isDefined should equal (true)
      val m2 = jaxrs.Router invokePrivate findMethodAndGenerateContext("/test/{id}", "/test/5",testCMethods, "application/json") getOrElse(throw new Exception("should find method"))
      m2._1.getName should equal("t")
      m2._2.toString should equal("Map(id -> 5)")
  }

  "A jaxrs.Router"  should "be able to invoke an action method" in {
    val invokeMethod = PrivateMethod[play.mvc.Result]('invokeMethod)
    val global = new play.GlobalSettings {
       override def getControllerInstance[A](controllerClass: Class[A]): A = controllerClass.newInstance
    }
    val m = classOf[TestController].getMethods()(0)
    val extracedArgumentValues = Map("id"->"5")
    val req = JavaHelpers.createJavaRequest(FakeRequest("GET","/test/5?foo=boo"))
    val result = jaxrs.Router invokePrivate invokeMethod(classOf[TestController],global, m,extracedArgumentValues, req)
    contentAsString(result.getWrappedResult) should equal ("id=5 q=boo")
  }

}