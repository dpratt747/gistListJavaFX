package http.requests

import cats.effect.IO
import cats.effect.std.Console
import cats.implicits.*
import configuration.GithubConfig
import domain.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import sttp.capabilities
import sttp.client3.*
import domain.optionMapFileNameGistInformationDecoder
import fs2.Stream

trait GithubRequestsAlg {
  def getAllPublicGists: Stream[IO, GithubGetGistsResponse]
  def getGistFileContent(url: RAW_URL): IO[FileContents]
}

private final class GithubRequestsImpl(
    config: GithubConfig,
    backend: SttpBackend[IO, capabilities.WebSockets]
) extends GithubRequestsAlg {

  private def requestHeaders: Map[String, String] = {
    Map(
      "Accept" -> "application/vnd.github+json",
      "Authorization" -> s"Bearer ${config.token}",
      "X-GitHub-Api-Version" -> "2022-11-28"
    )
  }

  override def getAllPublicGists: Stream[IO, GithubGetGistsResponse] = {
    val request = basicRequest.get(uri"${config.api}").headers(requestHeaders)

    Stream
      .eval(
        for {
          response <- request.send(backend)
          body <- IO
            .fromEither(
              response.body.leftMap(errorString =>
                new RuntimeException(
                  s"unable to parse response json: $errorString"
                )
              )
            )
            .handleErrorWith(error =>
              Console[IO].println(error) *> IO.raiseError(error)
            )
          decodedBody <- IO.fromEither(
            decode[Vector[GithubGetGistsResponse]](body)
          )
          _ <- Console[IO].println(
            s"Retrieved ${decodedBody.length} gists from the GitHub API"
          )
        } yield decodedBody
      )
      .flatMap(Stream.emits)
  }

  override def getGistFileContent(url: RAW_URL): IO[FileContents] = {
    val request = basicRequest.get(uri"$url").headers(requestHeaders)
    for {
      response <- request.send(backend)
      body <- IO
        .fromEither(
          response.body.leftMap(errorString =>
            new RuntimeException(s"unable to parse response json: $errorString")
          )
        )
        .handleErrorWith(error =>
          Console[IO].println(error) *> IO.raiseError(error)
        )
      content = FileContents.apply(body)
    } yield content
  }
}

object GithubRequestsImpl {
  def make(
      config: GithubConfig,
      backend: SttpBackend[IO, capabilities.WebSockets]
  ): GithubRequestsAlg = {
    new GithubRequestsImpl(config, backend)
  }
}
