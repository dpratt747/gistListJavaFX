package processing

import cats.effect.IO
import cats.effect.std.Console
import configuration.Config
import domain.*
import http.requests.{GeminiLLMAlg, GithubRequestsAlg}
import fs2.Stream
import scala.concurrent.duration.*
import fs2.text
import fs2.io.file.{Files, Path}

trait StreamProcessingAlg {
  def getFileContentsStream(gistStream: Stream[IO, GithubGetGistsResponse]): Stream[IO, (HTML_URL, Vector[FileContents])]

  def getGeminiSummary(gistStream: Stream[IO, (HTML_URL, Vector[FileContents])]): Stream[IO, (HTML_URL, GeminiSummary)]

  def writeSummaryToFile(geminiResultsStream: Stream[IO, (HTML_URL, GeminiSummary)]): IO[Unit]
}

private final class StreamProcessingImpl(config: Config, githubRequests: GithubRequestsAlg, geminiLLMAlg: GeminiLLMAlg) extends StreamProcessingAlg {
  override def getFileContentsStream(gistStream: Stream[IO, GithubGetGistsResponse]): Stream[IO, (HTML_URL, Vector[FileContents])] = {
    gistStream.parEvalMap(config.application.parStreams.getInt) { response =>
        response.files.map { (files: Map[FileName, GistFileInformation]) =>
          files.view.mapValues { values =>
            values.raw_url match {
              case Some(contentUrl) => githubRequests.getGistFileContent(contentUrl)
              case None => IO.pure(FileContents("no content found"))
            }
          }.values.toVector.sequence.map { fileContents =>
            Some(response.html_url.getOrElse(HTML_URL.apply("No gist found")) -> fileContents)
          }
        }.sequence.map(_.flatten)
      }
      .collect { case Some(gists) => gists }
  }

  override def getGeminiSummary(gistStream: Stream[IO, (HTML_URL, Vector[FileContents])]): Stream[IO, (HTML_URL, GeminiSummary)] = {
    gistStream.evalMap { case (url, fileContents) =>
      Console[IO].println(s"Processing gist $url") *>
        IO.sleep(config.gemini.queryDelaySeconds.getInt.second) *> (for {
        gistFileContents <- IO.pure(fileContents.mkString("\n"))
        answer <- geminiLLMAlg.getAIAnswer(gistFileContents)
      } yield (url, answer))
    }
  }

  override def writeSummaryToFile(geminiResultsStream: Stream[IO, (HTML_URL, GeminiSummary)]): IO[Unit] = {
    for {
      timestamp <- cats.effect.Clock[IO].realTime.map(_.toMillis)
      outputPath = Path(s"src/main/resources/gist_summary_$timestamp.csv")
      _ <- (Stream.emit("Gist URL, Summary\n") ++
        geminiResultsStream.map { case (url, summary) =>
          s"$url, \"${summary.getString.replaceAll("\n", " ").trim}\" \n"
        })
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(outputPath))
        .compile
        .drain
      _ <- Console[IO].println(s"File contents written to $outputPath")
    } yield ()
  }
}

object StreamProcessingImpl {
  def make(config: Config, githubRequests: GithubRequestsAlg, geminiLLMAlg: GeminiLLMAlg): StreamProcessingAlg = {
    new StreamProcessingImpl(config, githubRequests, geminiLLMAlg)
  }
}

