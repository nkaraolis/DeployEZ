import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ HttpHeader, HttpMethod, HttpRequest, headers }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._
import spray.json.{ JsObject, RootJsonFormat }

import scala.concurrent.Future
import scala.language.postfixOps

trait Deployer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val http = Http(system)

  def createAuthHeader(username: String, token: String): HttpHeader = {
    headers.Authorization(BasicHttpCredentials(username, token))
  }

  def createRequest(method: HttpMethod, url: String, authHeader: HttpHeader): HttpRequest = {
    HttpRequest(method, url).withHeaders(authHeader)
  }

  val serviceName: String

  private val ciOpenToken = System.getenv("ci_open_token")
  val ciOpenBase = System.getenv("ci_open_base")
  lazy val ciDevBase = System.getenv("ci_dev_base")
  lazy val ciDevToken = System.getenv("ci_dev_token")
  lazy val ciDevAuth = createAuthHeader(user, ciDevToken)

  val user = System.getenv("jenkins_user")

  val ciOpenAuth = createAuthHeader(user, ciOpenToken)

  lazy val appBuild: HttpRequest = createRequest(GET, s"$ciOpenBase/$serviceName/api/json", ciOpenAuth)

  lazy val getBuildVersion = for {
    app <- http.singleRequest(appBuild)
    buildJson <- Unmarshal(app).to[JsObject]
    builds = buildJson.fields("builds").convertTo[List[Build]]
    lastBuildReq = createRequest(GET, builds.head.url + "/api/json", ciOpenAuth)
    lastBuild <- http.singleRequest(lastBuildReq)
    moreJson <- Unmarshal(lastBuild).to[JsObject]
    version = moreJson.fields("description").convertTo[String]
  } yield version

  def findBuild(builds: List[Build], appName: Parameter, version: Parameter, header: HttpHeader) = {
    def inner(l: List[Build]): Future[Option[String]] = l match {
      case Nil => Future.successful(None)
      case h :: tail =>
        http.singleRequest(createRequest(GET, h.url + "api/json", header)) flatMap { res =>
          Unmarshal(res).to[JsObject] flatMap { json =>
            val list = json.fields("actions").convertTo[List[JsObject]].head.fields("parameters").convertTo[List[Parameter]]

            if (list.contains(version) && list.contains(appName))
              Future.successful(Some(h.url + "api/json"))
            else
              inner(tail)
          }
        }
    }

    inner(builds)
  }

  def awaitBuildCompletion(req: HttpRequest): Future[Unit] = {
    http.singleRequest(req) flatMap { res =>
      Unmarshal(res).to[JsObject].flatMap { json =>
        if (json.fields("building").convertTo[Boolean]) {
          Thread.sleep(30000)
          awaitBuildCompletion(req)
        } else Future.unit
      }
    }
  }

}

case class Build(number: Int, url: String)

case class Parameter(name: String, value: String)

object Parameter {
  implicit val pFormat: RootJsonFormat[Parameter] = jsonFormat2(Parameter.apply)
}

object Build {
  implicit val buildFormat: RootJsonFormat[Build] = jsonFormat2(Build.apply)
}