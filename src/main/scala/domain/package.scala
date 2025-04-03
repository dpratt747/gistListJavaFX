import io.circe.*
import io.circe.generic.auto.*

package object domain {

  opaque type GeminiSummary = String

  object GeminiSummary {
    def apply(value: String): GeminiSummary = value

    extension (summary: GeminiSummary)
      def getString: String = summary
  }
  
  opaque type FileContents = String

  object FileContents {
    def apply(value: String): FileContents = value

    given Decoder[FileContents] = Decoder.decodeString.map(FileContents.apply)
  }

  opaque type HTML_URL = String

  object HTML_URL {
    def apply(value: String): HTML_URL = value

    given Decoder[HTML_URL] = Decoder.decodeString.map(HTML_URL.apply)
  }

  opaque type RAW_URL = String

  opaque type FileName = String

  opaque type Description = String

  object Description {
    def apply(value: String): Description = value

    given Decoder[Description] = Decoder.decodeString.map(Description.apply)
  }

  opaque type TimestampString = String

  object TimestampString {
    def apply(value: String): TimestampString = value

    given Decoder[TimestampString] = Decoder.decodeString.map(TimestampString.apply)
  }


  final case class GistFileInformation(filename: FileName, raw_url: Option[RAW_URL])

  final case class GithubGetGistsResponse(html_url: Option[HTML_URL], updated_at: Option[TimestampString], description: Option[Description], files: Option[Map[FileName, GistFileInformation]])

  given optionMapFileNameGistInformationDecoder: Decoder[Option[Map[FileName, GistFileInformation]]] = Decoder.decodeOption(Decoder.decodeMap[FileName, GistFileInformation])
}