package test

import org.scalatest._

import play.api.test._
import play.api.test.Helpers._
import org.scalatest.matchers.ShouldMatchers
import play.api.libs.ws.WS

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec extends FlatSpec with ShouldMatchers {
   val h = "http://127.0.0.1:3333"
   "A JAX-RS app" should "work in real context" in {
    running(TestServer(3333), HTMLUNIT) { browser =>
      browser.goTo(h+"/" )
      browser.pageSource should include("Your new application is ready.")
      val a = await(WS.url(h+"/assets/stylesheets/main.css").get)
      a.status should equal(200)
      val r = await(WS.url(h+"/id/5?foo=6").get)
      r.status should equal(200)
      r.body.toString should include ("id=5 query=6 delegate=yay")
      val optional = await(WS.url(h+"/id/5").get)
      optional.status should equal(200)
      optional.body.toString should include ("id=5 query=booo delegate=yay")
    }
  }
  
}