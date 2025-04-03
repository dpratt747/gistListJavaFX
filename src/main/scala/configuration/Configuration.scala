package configuration

import pureconfig.*
import pureconfig.generic.semiauto.*

opaque type API = String

object API {
  def apply(string: String): API = string
}

opaque type Token = String

object Token {
  def apply(string: String): Token = string
}

opaque type NumberOfStreams = Int

object NumberOfStreams {
  def apply(int: Int): NumberOfStreams = int

  extension (numberOfStreams: NumberOfStreams)
    def getInt: Int = numberOfStreams
}

opaque type ModelName = String

object ModelName {
  def apply(string: String): ModelName = string

  extension (modelName: ModelName)
    def getString: String = modelName
}

opaque type ApiKey = String

object ApiKey {
  def apply(string: String): ApiKey = string

  extension (apiKey: ApiKey)
    def getString: String = apiKey
}

opaque type QueryDelaySeconds = Int

object QueryDelaySeconds {
  def apply(int: Int): QueryDelaySeconds = int

  extension (seconds: QueryDelaySeconds)
    def getInt: Int = seconds
}


opaque type TotalQueryTimeoutInMins = Int

object TotalQueryTimeoutInMins {
  def apply(int: Int): TotalQueryTimeoutInMins = int

  extension (seconds: TotalQueryTimeoutInMins)
    def getInt: Int = seconds
}

final case class ApplicationConfig(parStreams: NumberOfStreams)

final case class GithubConfig(api: API, token: Token)

final case class GeminiConfig(modelName: ModelName, apiKey: ApiKey, queryDelaySeconds: QueryDelaySeconds, totalQueryTimeoutInMins: TotalQueryTimeoutInMins)

final case class Config(github: GithubConfig, application: ApplicationConfig, gemini: GeminiConfig)

trait ConfigurationAlg {
  def config: Config
}

private final class ConfigurationImpl(source: ConfigObjectSource)(using ConfigReader[Config]) extends ConfigurationAlg {

  override def config: Config = source.loadOrThrow[Config]
}

object ConfigurationImpl {

  given ConfigReader[API] = ConfigReader.fromCursor[API] { cur =>
    cur.asString.map(API.apply)
  }

  given ConfigReader[Token] = ConfigReader.fromCursor[Token] { cur =>
    cur.asString.map(Token.apply)
  }

  given ConfigReader[NumberOfStreams] = ConfigReader.fromCursor[NumberOfStreams] { cur =>
    cur.asInt.map(NumberOfStreams.apply)
  }

  given ConfigReader[ModelName] = ConfigReader.fromCursor[ModelName] { cur =>
    cur.asString.map(ModelName.apply)
  }

  given ConfigReader[ApiKey] = ConfigReader.fromCursor[ApiKey] { cur =>
    cur.asString.map(ApiKey.apply)
  }

  given ConfigReader[QueryDelaySeconds] = ConfigReader.fromCursor[QueryDelaySeconds] { cur =>
    cur.asInt.map(QueryDelaySeconds.apply)
  }

  given ConfigReader[TotalQueryTimeoutInMins] = ConfigReader.fromCursor[TotalQueryTimeoutInMins] { cur =>
    cur.asInt.map(TotalQueryTimeoutInMins.apply)
  }

  given ConfigReader[Config] = deriveReader[Config]

  def make(source: ConfigObjectSource): ConfigurationAlg = {
    new ConfigurationImpl(source)
  }
}