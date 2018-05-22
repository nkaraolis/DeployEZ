import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods.{ GET, POST }
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object QADeploy extends Deployer {
  override val serviceName = args.head

  //get build version: ci-open
  //create a release: ci-open
  //create a release slug: ci-build
  //deploy microservice: orch-qa

  def createRelease(version: String): Future[Option[String]] = {
    val req = createRequest(
      POST,
      s"$ciOpenBase/create-a-release/buildWithParameters?RELEASE_CANDIDATE_VERSION=$version&ARTEFACT_NAME=$serviceName",
      ciOpenAuth
    )
    for {
      _ <- http.singleRequest(req)
      _ = Thread.sleep(10000)
      createReleases = createRequest(GET, s"$ciOpenBase/create-a-release/api/json", ciOpenAuth)
      releases <- http.singleRequest(createReleases)
      json <- Unmarshal(releases).to[JsObject]
      releaseBuilds = json.fields("builds").convertTo[List[Build]]
      ourBuild <- findBuild(releaseBuilds, Parameter("ARTEFACT_NAME", serviceName), Parameter("RELEASE_CANDIDATE_VERSION", version), ciOpenAuth)
    } yield ourBuild
  }

  val deployQA = for {
    version <- getBuildVersion
    a <- createRelease(version)
  } yield a

  val realVersion = Await.result(deployQA, 3 minutes)

  println(serviceName)

  system.terminate()
}
