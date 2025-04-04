package ui

import domain.{GeminiSummary, HTML_URL}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import javafx.application.Application
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.paint.Color
import javafx.stage.{Stage, StageStyle}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

final case class ApiKeys(githubKey: String, geminiKey: String)

object ApiKeys {
  implicit val decoder: Decoder[ApiKeys] = deriveDecoder[ApiKeys]
  implicit val encoder: Encoder[ApiKeys] = deriveEncoder[ApiKeys]
}

final case class SummaryRow(url: String, summary: String)

class MainUI(app: Application) {
  private val keysFile = Paths.get("api_keys.json")
  private var submitButton: Option[Button] = None
  private var summaryStage: Option[Stage] = None
  private var mainStage: Option[Stage] = None
  private var counterLabel: Option[Label] = None
  private var processedCount: Long = 0
  private var mainLoadingIndicator: Option[ProgressBar] = None
  private var currentSummaries: List[(HTML_URL, GeminiSummary)] = List.empty
  private var showSummariesButton: Option[Button] = None

  def setMainStage(stage: Stage): Unit = {
    mainStage = Some(stage)
    stage.setOnCloseRequest(_ => {
      closeAllWindows()
    })
  }

  def closeAllWindows(): Unit = {
    summaryStage.foreach(_.close())
    summaryStage = None
    mainStage.foreach(_.close())
    mainStage = None
  }

  def loadKeys(): Option[ApiKeys] = {
    if (Files.exists(keysFile)) {
      val json =
        new String(Files.readAllBytes(keysFile), StandardCharsets.UTF_8)
      decode[ApiKeys](json).toOption
    } else None
  }

  def saveKeys(keys: ApiKeys): Unit = {
    val json = keys.asJson.spaces2
    Files.write(keysFile, json.getBytes(StandardCharsets.UTF_8))
  }

  private def createUrlColumn: TableColumn[SummaryRow, String] = {
    new TableColumn[SummaryRow, String]("Gist URL") {
      setCellValueFactory(cellData =>
        new SimpleStringProperty(cellData.getValue.url)
      )
      setCellFactory(_ =>
        new javafx.scene.control.TableCell[SummaryRow, String] {
          val hyperlink = new Hyperlink
          setGraphic(hyperlink)
          setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY)
          itemProperty().addListener((_, _, newValue) => {
            if (newValue != null) {
              hyperlink.setText(newValue)
              hyperlink.setOnAction(_ =>
                app.getHostServices.showDocument(newValue)
              )
            }
          })
        }
      )
      setPrefWidth(300)
    }
  }

  private def createSummaryColumn: TableColumn[SummaryRow, String] = {
    new TableColumn[SummaryRow, String]("Summary") {
      setCellValueFactory(cellData =>
        new SimpleStringProperty(cellData.getValue.summary)
      )
      setPrefWidth(500)
      setCellFactory(_ =>
        new javafx.scene.control.TableCell[SummaryRow, String] {
          setWrapText(true)
          setStyle("-fx-alignment: CENTER-LEFT;")
          itemProperty().addListener((_, _, newValue) => {
            if (newValue != null) {
              setText(newValue)
              setTooltip(new Tooltip(newValue))
            }
          })
          setOnMouseClicked(_ => {
            if (getText != null) {
              val dialog = new TextArea(getText) {
                setWrapText(true)
                setEditable(false)
                setPrefRowCount(10)
                setPrefColumnCount(80)
              }
              val dialogStage = new Stage(StageStyle.DECORATED) {
                setTitle("Full Summary")
                setScene(new Scene(dialog) {
                  setFill(Color.WHITE)
                })
                setWidth(600)
                setHeight(400)
              }
              dialogStage.show()
            }
          })
        }
      )
    }
  }

  private def createTableView(
                               summary: List[(HTML_URL, GeminiSummary)]
                             ): TableView[SummaryRow] = {
    println(s"Creating table view with ${summary.size} items")
    val tableView = new TableView[SummaryRow] {
      setId("summaryTableView")
      setStyle("-fx-background-color: white;")
      setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY)
    }

    tableView.getColumns.setAll(createUrlColumn, createSummaryColumn)
    if (summary.isEmpty) {
      tableView.setPlaceholder(
        new Label(
          "Loading gist summaries... Please enter your API keys and click Submit."
        ) {
          setStyle("-fx-text-fill: #666666; -fx-font-size: 14px;")
        }
      )
    } else {
      tableView.setPlaceholder(null)
      val items = FXCollections.observableArrayList(
        summary.map { case (url, summary) =>
          val row = SummaryRow(
            url.toString,
            summary.getString.replaceAll("\n", " ").trim
          )
          row
        } *
      )
      tableView.setItems(items)
      println(s"Table view created with ${items.size()} items")
    }
    tableView
  }

  private def createLoadingIndicator(): ProgressIndicator = {
    new ProgressIndicator {
      setId("loadingIndicator")
      setVisible(false)
      setStyle("-fx-progress-color: #4CAF50;")
      setPrefSize(30, 30)
      setMaxSize(30, 30)
    }
  }

  private def createCloseButton(stage: Stage): Button = {
    new Button {
      setText("Close")
      setStyle("-fx-background-color: #F44336; -fx-text-fill: white;")
      setOnAction(_ => {
        stage.close()
        summaryStage = None
        submitButton.foreach(_.setDisable(false))
      })
    }
  }

  def showLoadingIndicator(show: Boolean): Unit = {
    summaryStage.foreach { stage =>
      val loadingIndicator = stage.getScene
        .lookup("#loadingIndicator")
        .asInstanceOf[ProgressIndicator]
      if (loadingIndicator != null) {
        loadingIndicator.setVisible(show)
      }
    }
  }

  def reenableSubmitButton(): Unit = {
    submitButton.foreach(_.setDisable(false))
  }

  def disableSubmitButton(): Unit = {
    submitButton.foreach(_.setDisable(true))
  }

  def updateSummaryWindow(url: HTML_URL, summary: GeminiSummary): Unit = {
    // Store the new summary
    currentSummaries = currentSummaries :+ (url, summary)
    // Enable the show summaries button when we have summaries
    showSummariesButton.foreach(_.setDisable(false))

    summaryStage.foreach { stage =>
      val tableView = stage.getScene
        .lookup("#summaryTableView")
        .asInstanceOf[TableView[SummaryRow]]
      if (tableView != null) {
        val currentItems = tableView.getItems
        val newRow =
          SummaryRow(url.toString, summary.getString.replaceAll("\n", " ").trim)
        currentItems.add(newRow)
        println(s"Added new summary: ${newRow.url}")
      } else {
        println("Error: Could not find table view in scene")
      }
    }
  }

  def updateCounter(): Unit = {
    processedCount += 1
    counterLabel.foreach(_.setText(s"Processed: $processedCount"))
  }

  def showSummaryWindow(summary: List[(HTML_URL, GeminiSummary)]): Unit = {
    println(s"Showing summary window with ${summary.size} items")
    // Close existing window if any
    summaryStage.foreach(_.close())
    summaryStage = None

    // Set counter to current number of summaries
    processedCount = currentSummaries.size

    // Create new window
    println("Creating new summary window")
    val stage = new Stage(StageStyle.DECORATED) {
      setTitle("Gist Summaries")
      setWidth(800)
      setHeight(600)
      setX(100)
      setY(100)
      toBack()
    }
    summaryStage = Some(stage)

    val loadingIndicator = createLoadingIndicator()
    val counter = createCounterLabel()
    counterLabel = Some(counter)
    // Update counter text with current count
    counter.setText(s"Processed: $processedCount")

    val topBar = new javafx.scene.layout.HBox {
      setSpacing(10)
      setAlignment(javafx.geometry.Pos.CENTER_LEFT)
      getChildren.addAll(loadingIndicator, counter)
    }

    val vbox = new VBox {
      setSpacing(10)
      setPadding(new Insets(20))
      getChildren.addAll(
        topBar,
        createTableView(currentSummaries),
        createCloseButton(stage)
      )
    }

    val scene = new Scene(vbox) {
      setFill(Color.WHITE)
    }

    stage.setScene(scene)
    stage.show()
    stage.toBack()
    println("New summary window created and shown")
  }

  private def createLabel(text: String): Label = {
    new Label {
      setText(text)
      setStyle("-fx-text-fill: black; -fx-font-size: 14px;")
    }
  }

  private def readmeLink(): Hyperlink = {
    val hyperlink = new Hyperlink("README.md")
    hyperlink.setOnAction(_ =>
      app.getHostServices.showDocument("https://github.com/dpratt747/gistListJavaFX/blob/master/README.md")
    )
    hyperlink
  }


  private def createTextField(
                               prompt: String,
                               initialValue: String
                             ): TextField = {
    new TextField {
      setPromptText(prompt)
      setStyle(
        "-fx-background-color: #333333; -fx-text-fill: white; -fx-prompt-text-fill: #888888;"
      )
      setText(initialValue)
    }
  }

  private def createCheckBox: CheckBox = {
    new CheckBox {
      setText("Save keys")
      setStyle("-fx-text-fill: black;")
      setSelected(true)
    }
  }

  private def createSubmitButton(
                                  githubField: TextField,
                                  geminiField: TextField,
                                  saveCheckbox: CheckBox,
                                  onSubmit: (String, String, Boolean) => Unit
                                ): Button = {
    val button = new Button {
      setText("Get Gists Summaries")
      setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;")
      setOnAction(_ => {
        val githubAPIKey = githubField.getText
        val geminiKey = geminiField.getText

        onSubmit(githubAPIKey, geminiKey, saveCheckbox.isSelected)
        setDisable(true)
        showSummariesButton(true)
      })
    }
    submitButton = Some(button)
    
    // Function to check if both fields are filled
    def updateButtonState(): Unit = {
      val githubKeyFilled = githubField.getText != null && githubField.getText.trim.nonEmpty
      val geminiKeyFilled = geminiField.getText != null && geminiField.getText.trim.nonEmpty
      button.setDisable(!(githubKeyFilled && geminiKeyFilled))
    }
    
    // Add listeners to both text fields
    githubField.textProperty().addListener((_, _, _) => updateButtonState())
    geminiField.textProperty().addListener((_, _, _) => updateButtonState())
    
    // Initial state
    updateButtonState()
    
    button
  }

  def showMainLoadingIndicator(show: Boolean): Unit = {
    mainLoadingIndicator.foreach(_.setVisible(show))
  }

  private def createCounterLabel(): Label = {
    new Label {
      setId("counterLabel")
      setStyle(
        "-fx-text-fill: #333333; -fx-font-size: 14px; -fx-font-weight: bold;"
      )
      setText("Processed: 0")
    }
  }

  def showSummariesButton(show: Boolean): Unit = {
    showSummariesButton.foreach(_.setVisible(show))
  }

  private def createShowSummariesButton(): Button = {
    new Button {
      setText("Show Summaries")
      setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;")
      setVisible(false)
      setDisable(true) // Initially disabled
      setOnAction(_ => {
        if (currentSummaries.nonEmpty) {
          showSummaryWindow(currentSummaries)
        }
      })
    }
  }

  def createMainScene(
                       savedKeys: Option[ApiKeys],
                       onSubmit: (String, String, Boolean) => Unit
                     ): Scene = {
    val githubField = createTextField(
      "Enter GitHub API Key",
      savedKeys.map(_.githubKey).getOrElse("")
    )
    val geminiField = createTextField(
      "Enter Gemini API Key",
      savedKeys.map(_.geminiKey).getOrElse("")
    )
    val saveCheckbox = createCheckBox

    val loadingIndicator = new ProgressBar {
      setId("mainLoadingIndicator")
      setVisible(false)
      setStyle("-fx-accent: #4CAF50;")
      setPrefWidth(200)
      setProgress(-1.0)
    }
    mainLoadingIndicator = Some(loadingIndicator)

    val submitButton =
      createSubmitButton(githubField, geminiField, saveCheckbox, onSubmit)
    val summariesButton = createShowSummariesButton()
    showSummariesButton = Some(summariesButton)
    val buttonContainer: HBox = new javafx.scene.layout.HBox {
      setSpacing(10)
      setAlignment(javafx.geometry.Pos.CENTER)
      val leftSpacer = new javafx.scene.layout.Region
      val rightSpacer = new javafx.scene.layout.Region
      javafx.scene.layout.HBox
        .setHgrow(leftSpacer, javafx.scene.layout.Priority.ALWAYS)
      javafx.scene.layout.HBox
        .setHgrow(rightSpacer, javafx.scene.layout.Priority.ALWAYS)
      getChildren.addAll(
        leftSpacer,
        submitButton,
        loadingIndicator,
        summariesButton,
        rightSpacer
      )
    }


    val vbox = new VBox {
      setSpacing(10)
      setPadding(new Insets(20))
      getChildren.addAll(
        createLabel("GitHub API Key"),
        githubField,
        createLabel("Gemini API Key"),
        geminiField,
        saveCheckbox,
        buttonContainer,
        readmeLink()
      )
    }

    new Scene(vbox) {
      setFill(Color.BLACK)
    }
  }
}
