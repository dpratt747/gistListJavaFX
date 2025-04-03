package http.requests

import cats.effect.IO
import configuration.GeminiConfig
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import domain.*

trait GeminiLLMAlg {
  def getAIAnswer(pageData: String): IO[GeminiSummary]
}

private final class GeminiLLMImpl(model: GoogleAiGeminiChatModel) extends GeminiLLMAlg {
  override def getAIAnswer(pageData: String): IO[GeminiSummary] = {
    IO.blocking(model.chat(
      s"""
         |Summarise the following into 2 sentences:
         |
         |$pageData
         |
         |""".stripMargin)).map(GeminiSummary.apply)
  }
}

object GeminiLLMImpl {
  def make(config: GeminiConfig): GeminiLLMAlg = {
    new GeminiLLMImpl(GoogleAiGeminiChatModel.builder()
      .apiKey(config.apiKey.getString)
      .modelName(config.modelName.getString)
      .build())
  }
}