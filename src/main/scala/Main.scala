import cats.effect.IO
import cats.effect.unsafe.implicits.global
import configuration.*
import http.requests.{GeminiLLMImpl, GithubRequestsImpl}
import javafx.application.{Application, Platform}
import javafx.stage.Stage
import processing.StreamProcessingImpl
import pureconfig.ConfigSource
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import ui.{ApiKeys, MainUI}

import scala.concurrent.duration.*


class MainApp extends Application {
  private val ui = new MainUI(this)

  private def processKeys(githubAPIKey: String, geminiKey: String): Unit = {
    def program(backend: SttpBackend[IO, capabilities.WebSockets]): IO[Unit] = {
      for {
        oldConfig <- IO(ConfigurationImpl.make(ConfigSource.default).config)
        config = oldConfig
          .copy(gemini = oldConfig.gemini.copy(apiKey = ApiKey.apply(geminiKey)))
          .copy(github = oldConfig.github.copy(token = Token(githubAPIKey)))

        githubRequests = GithubRequestsImpl.make(config.github, backend)
        ai = GeminiLLMImpl.make(config.gemini)
        streamProcessor = StreamProcessingImpl.make(config, githubRequests, ai)

        gistStream = githubRequests.getAllPublicGists
        fileContentsStream = streamProcessor.getFileContentsStream(gistStream)
        geminiResultsStream = streamProcessor.getGeminiSummary(fileContentsStream)

        _ <- geminiResultsStream.evalMap { case (url, summary) =>
          IO {
            println(s"Processing summary for URL: $url")
            Platform.runLater(() => {
              ui.updateSummaryWindow(url, summary)
              ui.updateCounter()
            })
          }
        }.compile.drain.timeout(5.minutes)
      } yield ()
    }

    // Show empty summary window before starting processing
    Platform.runLater(() => {
      ui.showSummaryWindow(List.empty)
      ui.showLoadingIndicator(true)
      ui.disableSubmitButton()
      ui.showMainLoadingIndicator(true)
    })

    // Run processing in a separate thread
    new Thread(() => {
      try {
        HttpClientCatsBackend.resource[IO]().use { backend =>
          IO(println("Starting to retrieve public gists")) *> program(backend)
        }.unsafeRunSync()
      } finally {
        // Ensure submit button is re-enabled and loading indicator is hidden
        Platform.runLater(() => {
          ui.reenableSubmitButton()
          ui.showLoadingIndicator(false)
          ui.showMainLoadingIndicator(false)
          ui.showSummariesButton(true)
        })
      }
    }).start()
  }

  override def start(primaryStage: Stage): Unit = {
    // Show summary window first, but ensure it stays behind
    Platform.runLater(() => {
      ui.showSummaryWindow(List.empty)
      // Show API keys window on top
      primaryStage.setTitle("API Keys Input")
      primaryStage.setWidth(600)
      primaryStage.setHeight(300)

      val savedKeys = ui.loadKeys()
      val scene = ui.createMainScene(savedKeys, (githubKey, geminiKey) => {
        ui.saveKeys(ApiKeys(githubKey, geminiKey))
        processKeys(githubKey, geminiKey)
      })

      primaryStage.setScene(scene)
      ui.setMainStage(primaryStage)
      primaryStage.show()
    })
  }
}

object Main {

  def main(args: Array[String]): Unit = {
    // Start JavaFX on the main thread
    Platform.startup(() => {})
    // Launch the application
    Application.launch(classOf[MainApp], args *)
  }
}
  
