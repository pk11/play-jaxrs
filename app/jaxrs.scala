package org.pk11.jaxrs

import play.api.mvc.{ Result, Handler }
import play.core.j.JavaAction
import org.reflections.{ Reflections, ReflectionUtils }
import org.reflections.util.{ ClasspathHelper, ConfigurationBuilder, FilterBuilder }
import org.reflections.scanners.{ TypeAnnotationsScanner }
import scala.collection.JavaConverters._
import java.lang.reflect.{ Method, InvocationTargetException }
import javax.ws.rs._
import java.lang.reflect.Method
import java.lang.annotation.Annotation
import com.google.common.base.Predicates
import com.google.common.base.Optional

/**
 * Provides an alterantive, JAX-RS based routing mechanism for java Play applications
 * usage:
 * {{{
 *    public class Global extends GlobalSettings {
 *      @Override
 *      public play.api.mvc.Handler onRouteRequest(RequestHeader request) {
 *          return org.pk11.jaxrs.Router.handlerFor(this, request);
 *       }
 *    }
 *
 * }}}
 */
object Router {

  private lazy val routerPackage = play.api.Play.maybeApplication.flatMap(_.configuration.getString("jaxrc.package")).getOrElse("controllers")
  private lazy val assetServing = play.api.Play.maybeApplication.flatMap(_.configuration.getString("jaxrc.assets.serving"))

  private lazy val ref = new Reflections(
    new ConfigurationBuilder()
      .filterInputsBy(new FilterBuilder().include(routerPackage + ".*"))
      .setScanners(new TypeAnnotationsScanner)
      .setUrls(ClasspathHelper.forPackage(routerPackage)))

  //TODO: find out why ref.getTypesAnnotatedWith(classOf[Path]) is not working  
  private lazy val classes = ref.getStore.getTypesAnnotatedWith(classOf[Path].getName).asScala.map(Class.forName(_)).toSet

  private lazy val urlParamCapture = "\\{(.*?)\\}"

  private def slashOrNot(s: String) = if (s.startsWith("/")) s else "/"+s

  private def findHttpMethodAnnotation(httpMethod: String): Class[_ <: java.lang.annotation.Annotation] = httpMethod match {
    case "GET" => classOf[GET]
    case "POST" => classOf[POST]
    case "PUT" => classOf[PUT]
    case "DELETE" => classOf[DELETE]
    case "HEAD" => classOf[HEAD]
    case "OPTIONS" => classOf[OPTIONS]
  }

  //find class level annotation
  private def isResourcePath(uri: String, path: String): Boolean = {
    val r = path.replaceAll(urlParamCapture, "(.*)").r
    if (!r.findAllMatchIn(uri).toList.isEmpty) true
    else false
  }

  private def findMethodAndGenerateContext(rootPath: String, uri: String, methods: java.util.Set[Method], contentType: String): Option[(Method, Map[String, String])] = {
    //include rootPath only for non root slash class level @Path values or if the uri is root slash
    val rootPathPrefix = if (rootPath == "/" && uri != "/") "" else rootPath
    def toMetaData(m: Method): Option[(Method, Map[String, String])] = {
      val consumes = Option(m.getAnnotation(classOf[Consumes])).map(_.value).getOrElse(Array(contentType))
      if (consumes.contains(contentType)) {
        val mPath = Option(m.getAnnotation(classOf[Path])).map(p=> slashOrNot(p.value))
        val combined = rootPathPrefix + mPath.getOrElse("")
        //any variables?
        if (combined.contains("{") && combined.contains("}")) {
          val r = combined.replaceAll(urlParamCapture, "(.*)").r
          val rootParamNames = (for (m <- urlParamCapture.r.findAllIn(combined).matchData; e <- m.subgroups) yield e).toList
          val rootParamValues = (for (m <- r.findAllIn(uri).matchData; e <- m.subgroups) yield e).toList
          val methodMetaData = (rootParamNames zip rootParamValues).toMap
          rootParamValues.headOption.map(_ => Some(m, methodMetaData)).getOrElse(None)
        } else uri.r.findAllIn(combined).toList.headOption.map(_ => Some(m, Map[String, String]())).getOrElse(None)
      } else None
    }
    methods.asScala.map(toMetaData(_)).find(_.isDefined).flatten
  }

  private def invokeMethod(targetClass: Class[_], global: play.GlobalSettings, method: Method, extracedArgumentValues: Map[String, String], r: play.mvc.Http.RequestHeader): play.mvc.Result = {
    try {
      val argValues = method.getParameterAnnotations.map { arg =>
        lazy val pathParam = arg.find(_.isInstanceOf[PathParam]).map(_.asInstanceOf[PathParam])
        lazy val queryParam = arg.find(_.isInstanceOf[QueryParam]).map(_.asInstanceOf[QueryParam])
        if (pathParam.isDefined) {
          extracedArgumentValues.get(pathParam.get.value).getOrElse(throw new IllegalArgumentException("can not find annotation value for argument " + pathParam.get.value.toString + "in " + targetClass.toString + "#" + method.toString))
        } else if (queryParam.isDefined) 
          Optional.fromNullable(r.getQueryString(queryParam.get.value))
        else throw new IllegalArgumentException("can not find an appropriate JAX-RC annotation for an argument for method:" + targetClass.toString + "#" + method.toString)
      }
      return method.invoke(global.getControllerInstance(targetClass), argValues: _*).asInstanceOf[play.mvc.Result]
    } catch {
      case cause: InvocationTargetException => {
        println("Exception occured while trying to invoke: "+targetClass.getName+"#"+method.getName+" with "+extracedArgumentValues + " for uri:" + r.path)
        throw cause.getCause
      }
    }
  }

  private def findLongestMatch(classes: Set[Class[_]], r: play.mvc.Http.RequestHeader, appPath: String): Option[(Class[_], String)] = {
    classes.toList.collect { case c: Class[_] if isResourcePath(r.path, appPath + slashOrNot(c.getAnnotation(classOf[Path]).value)) => (c, appPath + slashOrNot(c.getAnnotation(classOf[Path]).value)) }.sortWith((a, b) => a._2.length > b._2.length).headOption
  }

  private def relevantMethods(c: Class[_], a: Class[_ <: java.lang.annotation.Annotation]): java.util.Set[Method] = ReflectionUtils.getAllMethods(c, Predicates.and(ReflectionUtils.withAnnotation[Method](a)))

  /**
   * provides a handler for given request
   * @param global the global of the current play application
   * @param r request header
   * @return action handler
   *
   */
  def handlerFor(global: play.GlobalSettings, r: play.mvc.Http.RequestHeader): Handler = {
    
    val appPath = Option(global.getClass.getAnnotation(classOf[ApplicationPath])).map(_.value).getOrElse("")

    assetServing.flatMap { assetsServing =>
      val mapping = assetsServing.split(",")
      if (mapping.size == 2 && r.path.startsWith(mapping(0))) 
        Some(controllers.Assets.at(path = mapping(1), r.path.replaceFirst(mapping(0), "")))
      else
        None
    }.getOrElse {
      
      val potentialClass = findLongestMatch(classes, r, appPath)
      potentialClass.map { targetClassWithPath =>
        val httpMethodClass = findHttpMethodAnnotation(r.method.toUpperCase)
        val methods: java.util.Set[Method] = relevantMethods(targetClassWithPath._1, httpMethodClass)
        val targetMethod = findMethodAndGenerateContext(targetClassWithPath._2, r.path, methods, Option(r.headers.get("Content-Type")).map(_(0)).getOrElse(""))
        //find target method
        targetMethod.map { methodWithContext =>
          val produces = Option(methodWithContext._1.getAnnotation(classOf[Produces]))

          //produce javaAction
          new JavaAction {
            def invocation: play.mvc.Result = {
              lazy val result = invokeMethod(targetClassWithPath._1, global, methodWithContext._1, methodWithContext._2, r)
              produces.map(p => new WrapProducer(p.value()(0).toString, result)).getOrElse(result)
            }
            def controller: Class[_] = targetClassWithPath._1
            def method: java.lang.reflect.Method = methodWithContext._1
          }
        }.getOrElse(null)
      }.getOrElse(null)
    }
  }

  /**
   * provides a wrapper for setting content type for given result
   */
  class WrapProducer(produces: String, r: play.mvc.Result) extends play.mvc.Result {
    def getWrappedResult(): Result = {
      r.getWrappedResult.as(produces)
    }
  }
}
