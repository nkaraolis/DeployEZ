import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpHeader, HttpMethod, HttpRequest, headers}
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.language.postfixOps

trait Deployer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val http = Http(system)

  private def createAuthHeader(username: String, token: String): HttpHeader = {
    headers.Authorization(BasicHttpCredentials(username, token))
  }

  def createRequest(method: HttpMethod, url: String, authHeader: HttpHeader) = {
    HttpRequest(method, url).withHeaders(authHeader)
  }

  private val ciOpenToken = System.getenv("ci_open_token")
  private val ciDevToken = System.getenv("ci_dev_token")
  private val orchDevToken = System.getenv("orch_dev_token")
  val ciOpenBase = System.getenv("ci_open_base")
  val ciDevBase = System.getenv("ci_dev_base")
  val orchDevBase = System.getenv("orch_dev_base")

  private val user = System.getenv("jenkins_user")
  val ciOpenAuth = createAuthHeader(user, ciOpenToken)
  val ciDevAuth = createAuthHeader(user, ciDevToken)
  val orchDevAuth = createAuthHeader(user, orchDevToken)

  val serviceName: String

  lazy val appBuild: HttpRequest = createRequest(GET, s"$ciOpenBase/$serviceName/api/json", ciOpenAuth)
}

case class Build(number: Int, url: String)

case class Parameter(name: String, value: String)

object Parameter {
  implicit val pFormat: RootJsonFormat[Parameter] = jsonFormat2(Parameter.apply)
}

object Build {
  implicit val buildFormat: RootJsonFormat[Build] = jsonFormat2(Build.apply)
}