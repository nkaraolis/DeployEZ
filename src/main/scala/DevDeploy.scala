/**
 * Created by Nick Karaolis on 27/10/17.
 */

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object DevDeploy extends Deployer {
  override val serviceName = args.head

  private lazy val orchDevBase = System.getenv("orch_dev_base")
  private lazy val orchDevToken = System.getenv("orch_dev_token")
  private lazy val orchDevAuth = createAuthHeader(user, orchDevToken)

  val devSlug: String => String = version => s"$ciDevBase/buildWithParameters?APP_VERSION=$version&APP_NAME=$serviceName"

  def createReleaseSlug(version: String): Future[Option[String]] = {
    val devSlugReq = createRequest(POST, devSlug(version), ciDevAuth)
    for {
      _ <- http.singleRequest(devSlugReq)
      _ = Thread.sleep(10000)
      slugs = createRequest(GET, s"$ciDevBase/api/json", ciDevAuth)
      moreSlugs <- http.singleRequest(slugs)
      evenMoreJson <- Unmarshal(moreSlugs).to[JsObject]
      slugBuilds = evenMoreJson.fields("builds").convertTo[List[Build]]
      ourBuild <- findBuild(slugBuilds, Parameter("APP_NAME", serviceName), Parameter("APP_VERSION", version), ciDevAuth)
    } yield ourBuild
  }

  val deployDev = for {
    version <- getBuildVersion
    ourBuild <- createReleaseSlug(version)
    _ <- awaitBuildCompletion(createRequest(GET, ourBuild.get, ciDevAuth))
    devDeployReq = createRequest(POST, s"$orchDevBase?SERVICE_VERSION=$version&SERVICE=$serviceName", orchDevAuth)
    _ <- http.singleRequest(devDeployReq)
  } yield ourBuild

  val realVersion = Await.result(deployDev, 3 minutes)

  system.terminate()
}