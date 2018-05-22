/**
  * Created by Nick Karaolis on 27/10/17.
  */

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object DevDeploy extends Deployer {
  override val serviceName = args.head

  val devSlug: String => String = version => s"$ciDevBase/buildWithParameters?APP_VERSION=$version&APP_NAME=$serviceName"

  def findBuild(builds: List[Build], version: String, appName: String) = {
    def inner(l: List[Build]): Future[Option[String]] = l match {
      case Nil => Future.successful(None)
      case h :: tail =>
        http.singleRequest(createRequest(GET, h.url + "api/json", ciDevAuth)) flatMap { res =>
          Unmarshal(res).to[JsObject] flatMap { json =>
            val list = json.fields("actions").convertTo[List[JsObject]].head.fields("parameters").convertTo[List[Parameter]]

            if (list.contains(Parameter("APP_NAME", appName)) && list.contains(Parameter("APP_VERSION", version)))
              Future.successful(Some(h.url + "api/json"))
            else
              inner(tail)
          }
        }
    }

    inner(builds)
  }

  def checkBuildStatus(url: String): Future[Unit] = {
    http.singleRequest(createRequest(GET, url, ciDevAuth)) flatMap { res =>
      Unmarshal(res).to[JsObject].flatMap { json =>
        if (json.fields("building").convertTo[Boolean]) {
          Thread.sleep(30000)
          checkBuildStatus(url)
        } else Future.unit
      }
    }
  }

  val getBuildVersion = for {
    app <- http.singleRequest(appBuild)
    buildJson <- Unmarshal(app).to[JsObject]
    builds = buildJson.fields("builds").convertTo[List[Build]]
    lastBuildReq = createRequest(GET, builds.head.url + "/api/json", ciOpenAuth)
    lastBuild <- http.singleRequest(lastBuildReq)
    moreJson <- Unmarshal(lastBuild).to[JsObject]
    version = moreJson.fields("description").convertTo[String]
  } yield version

  def createReleaseSlug(version: String) = {
    val devSlugReq = createRequest(POST, devSlug(version), ciDevAuth)
    for {
      _ <- http.singleRequest(devSlugReq)
      _ = Thread.sleep(10000)
      slugs = createRequest(GET, s"$ciDevBase/api/json", ciDevAuth)
      moreSlugs <- http.singleRequest(slugs)
      evenMoreJson <- Unmarshal(moreSlugs).to[JsObject]
      slugBuilds = evenMoreJson.fields("builds").convertTo[List[Build]]
      ourBuild <- findBuild(slugBuilds, version, serviceName)
    } yield ourBuild
  }

  val deployDev = for {
    version <- getBuildVersion
    ourBuild <- createReleaseSlug(version)
    _ <- checkBuildStatus(ourBuild.get)
    devDeployReq = createRequest(POST, s"$orchDevBase?SERVICE_VERSION=$version&SERVICE=$serviceName", orchDevAuth)
    _ <- http.singleRequest(devDeployReq)
  } yield ourBuild

  val realVersion = Await.result(deployDev, 3 minutes)

  system.terminate()
}