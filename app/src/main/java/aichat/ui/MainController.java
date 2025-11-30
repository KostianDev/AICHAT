package aichat.ui;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MainController {
    
    @FXML private ImageView sourceImageView;
    @FXML private ImageView targetImageView;
    
    @FXML private ScrollPane sourceScrollPane;
    @FXML private ScrollPane targetScrollPane;
    
    @FXML private StackPane sourceImageContainer;
    @FXML private StackPane targetImageContainer;
    
    @FXML private SplitPane mainSplitPane;
    
    @FXML private ComboBox<ColorModel> colorModelCombo;
    @FXML private Spinner<Integer> kSpinner;
    
    @FXML private Button loadSourceButton;
    @FXML private Button loadTargetButton;
    @FXML private Button analyzeButton;
    @FXML private Button resynthesizeButton;
    
    @FXML private FlowPane sourcePalettePane;
    @FXML private FlowPane targetPalettePane;
    
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    
    private BufferedImage sourceImage;
    private BufferedImage targetImage;
    private BufferedImage resultImage;
    
    private ColorPalette sourcePalette;
    private ColorPalette targetPalette;
    
    private Stage resultStage;
    
    @FXML
    public void initialize() {
        colorModelCombo.getItems().addAll(ColorModel.values());
        colorModelCombo.setValue(ColorModel.RGB);
        
        SpinnerValueFactory<Integer> kFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 512, 8);
        kSpinner.setValueFactory(kFactory);
        kSpinner.setEditable(true);
        
        kSpinner.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                kSpinner.getEditor().setText(oldValue);
            }
        });
        
        kSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                try {
                    int value = Integer.parseInt(kSpinner.getEditor().getText());
                    value = Math.max(2, Math.min(512, value));
                    kSpinner.getValueFactory().setValue(value);
                } catch (NumberFormatException e) {
                    kSpinner.getValueFactory().setValue(8);
                }
            }
        });
        
        setupResponsiveImageViews();
        
        updateButtonStates();
        statusLabel.setText("Ready. Load images to begin.");
        progressBar.setProgress(0);
    }
    
    private void setupResponsiveImageViews() {
        sourceImageContainer.minWidthProperty().bind(sourceScrollPane.widthProperty());
        sourceImageContainer.minHeightProperty().bind(sourceScrollPane.heightProperty());
        
        targetImageContainer.minWidthProperty().bind(targetScrollPane.widthProperty());
        targetImageContainer.minHeightProperty().bind(targetScrollPane.heightProperty());
        
        sourceImageView.fitWidthProperty().bind(
            sourceScrollPane.widthProperty().subtract(40));
        sourceImageView.fitHeightProperty().bind(
            sourceScrollPane.heightProperty().subtract(40));
        
        targetImageView.fitWidthProperty().bind(
            targetScrollPane.widthProperty().subtract(40));
        targetImageView.fitHeightProperty().bind(
            targetScrollPane.heightProperty().subtract(40));
        
        mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
            mainSplitPane.setDividerPositions(0.5);
        });
    }
    
    @FXML
    private void handleLoadSource() {
        File file = showImageFileChooser("Select Source Image");
        if (file != null) {
            loadImage(file, true);
        }
    }
    
    @FXML
    private void handleLoadTarget() {
        File file = showImageFileChooser("Select Target Image");
        if (file != null) {
            loadImage(file, false);
        }
    }
    
    @FXML
    private void handleAnalyze() {
        if (sourceImage == null && targetImage == null) {
            showAlert("No Image", "Please load at least one image to analyze.");
            return;
        }
        
        ColorModel colorModel = colorModelCombo.getValue();
        int k = kSpinner.getValue();
        
        setProcessing(true, "Analyzing images...");
        
        Task<Void> analyzeTask = new Task<>() {
            private ColorPalette srcPal;
            private ColorPalette tgtPal;
            
            @Override
            protected Void call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(colorModel);
                
                if (sourceImage != null) {
                    srcPal = engine.analyze(sourceImage, k);
                }
                if (targetImage != null) {
                    tgtPal = engine.analyze(targetImage, k);
                }
                return null;
            }
            
            @Override
            protected void succeeded() {
                sourcePalette = srcPal;
                targetPalette = tgtPal;
                displayPalette(sourcePalettePane, sourcePalette);
                displayPalette(targetPalettePane, targetPalette);
                setProcessing(false, "Analysis complete.");
                updateButtonStates();
            }
            
            @Override
            protected void failed() {
                setProcessing(false, "Analysis failed: " + getException().getMessage());
            }
        };
        
        new Thread(analyzeTask).start();
    }
    
    @FXML
    private void handleResynthesize() {
        if (sourceImage == null || targetImage == null) {
            showAlert("Missing Images", "Please load both source and target images.");
            return;
        }
        
        if (sourcePalette == null || targetPalette == null) {
            showAlert("No Palettes", "Please analyze both images first to extract palettes.");
            return;
        }
        
        ColorModel colorModel = colorModelCombo.getValue();
        
        setProcessing(true, "Resynthesizing image...");
        
        final ColorPalette srcPal = sourcePalette;
        final ColorPalette tgtPal = targetPalette;
        
        Task<BufferedImage> resynthTask = new Task<>() {
            @Override
            protected BufferedImage call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(colorModel);
                return engine.resynthesize(targetImage, srcPal, tgtPal);
            }
            
            @Override
            protected void succeeded() {
                resultImage = getValue();
                showResultWindow(resultImage);
                setProcessing(false, "Resynthesis complete. Result shown in new window.");
            }
            
            @Override
            protected void failed() {
                setProcessing(false, "Resynthesis failed: " + getException().getMessage());
            }
        };
        
        new Thread(resynthTask).start();
    }
    
    private void showResultWindow(BufferedImage image) {
        if (resultStage == null) {
            resultStage = new Stage();
            resultStage.setTitle("AICHAT - Result");
        }
        
        Image fxImage = SwingFXUtils.toFXImage(image, null);
        ImageView imageView = new ImageView(fxImage);
        imageView.setPreserveRatio(true);
        
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #1e1e1e;");
        
        ScrollPane scrollPane = new ScrollPane(imageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
        
        Button saveButton = new Button("Save Image");
        saveButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; " +
                          "-fx-font-size: 14px; -fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");
        saveButton.setOnAction(e -> handleSaveResult());
        
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #e0e0e0; " +
                           "-fx-font-size: 14px; -fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");
        closeButton.setOnAction(e -> resultStage.close());
        
        HBox buttonBar = new HBox(12, saveButton, closeButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(16));
        buttonBar.setStyle("-fx-background-color: #252525;");
        
        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(buttonBar);
        root.setStyle("-fx-background-color: #1e1e1e;");
        
        int windowWidth = image.getWidth() + 40;
        int windowHeight = image.getHeight() + 100;
        
        Scene scene = new Scene(root, windowWidth, windowHeight);
        
        resultStage.setScene(scene);
        resultStage.setMinWidth(400);
        resultStage.setMinHeight(300);
        resultStage.show();
        resultStage.toFront();
        
        imageView.fitWidthProperty().bind(scrollPane.widthProperty().subtract(20));
        imageView.fitHeightProperty().bind(scrollPane.heightProperty().subtract(20));
    }
    
    private void handleSaveResult() {
        if (resultImage == null) {
            showAlert("No Result", "No result image to save.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Result Image");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PNG Image", "*.png")
        );
        fileChooser.setInitialFileName("result.png");
        
        File file = fileChooser.showSaveDialog(resultStage);
        
        if (file != null) {
            try {
                String path = file.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".png")) {
                    file = new File(path + ".png");
                }
                ImageIO.write(resultImage, "PNG", file);
                statusLabel.setText("Image saved: " + file.getName());
            } catch (IOException e) {
                showAlert("Save Error", "Failed to save image: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void handleExit() {
        if (resultStage != null) {
            resultStage.close();
        }
        Stage stage = (Stage) loadSourceButton.getScene().getWindow();
        stage.close();
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About AICHAT");
        alert.setHeaderText("AICHAT - Advanced Image Color Harmony Analysis and Transformation");
        alert.setContentText("Version 1.0\n\nKPI Igor Sikorsky, 2025");
        applyDarkStyleToDialog(alert);
        alert.showAndWait();
    }
    
    private File showImageFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );
        
        Stage stage = (Stage) loadSourceButton.getScene().getWindow();
        return fileChooser.showOpenDialog(stage);
    }
    
    private void loadImage(File file, boolean isSource) {
        statusLabel.setText("Loading: " + file.getName() + "...");
        
        String url = file.toURI().toString();
        
        Image preview = new Image(url, 800, 800, true, true, true);
        
        preview.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 1.0 && !preview.isError()) {
                if (isSource) {
                    sourceImageView.setImage(preview);
                    sourcePalette = null;
                    sourcePalettePane.getChildren().clear();
                } else {
                    targetImageView.setImage(preview);
                    targetPalette = null;
                    targetPalettePane.getChildren().clear();
                }
                updateButtonStates();
            }
        });
        
        Image fullImage = new Image(url, true);
        fullImage.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 1.0 && !fullImage.isError()) {
                if (isSource) {
                    sourceImageView.setImage(fullImage);
                } else {
                    targetImageView.setImage(fullImage);
                }
                
                loadBufferedImageAsync(file, isSource);
                
                statusLabel.setText("Loaded: " + file.getName() + 
                    " (" + (int)fullImage.getWidth() + "x" + (int)fullImage.getHeight() + ")");
            }
        });
        
        preview.errorProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                showAlert("Load Error", "Could not read image file.");
                statusLabel.setText("Ready.");
            }
        });
    }
    
    private void loadBufferedImageAsync(File file, boolean isSource) {
        Task<BufferedImage> task = new Task<>() {
            @Override
            protected BufferedImage call() throws IOException {
                return ImageIO.read(file);
            }
            
            @Override
            protected void succeeded() {
                if (isSource) {
                    sourceImage = getValue();
                } else {
                    targetImage = getValue();
                }
            }
        };
        new Thread(task).start();
    }
    
    private void displayImage(ImageView imageView, BufferedImage image) {
        Image fxImage = SwingFXUtils.toFXImage(image, null);
        imageView.setImage(fxImage);
    }
    
    private void displayPalette(FlowPane pane, ColorPalette palette) {
        pane.getChildren().clear();
        if (palette == null) return;
        
        for (ColorPoint color : palette.getColors()) {
            VBox colorBox = new VBox(4);
            colorBox.setAlignment(Pos.CENTER);
            
            Rectangle rect = new Rectangle(50, 50);
            rect.setFill(Color.rgb(
                clamp((int) color.c1()),
                clamp((int) color.c2()),
                clamp((int) color.c3())
            ));
            rect.setStroke(Color.web("#555555"));
            rect.setStrokeWidth(1);
            rect.setArcWidth(8);
            rect.setArcHeight(8);
            
            Label hexLabel = new Label(color.toHexString());
            hexLabel.setStyle("-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: #b0b0b0;");
            
            colorBox.getChildren().addAll(rect, hexLabel);
            pane.getChildren().add(colorBox);
        }
    }
    
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
    
    private void setProcessing(boolean processing, String message) {
        progressBar.setProgress(processing ? -1 : 0);
        statusLabel.setText(message);
        analyzeButton.setDisable(processing);
        resynthesizeButton.setDisable(processing);
    }
    
    private void updateButtonStates() {
        boolean hasSource = sourceImage != null;
        boolean hasTarget = targetImage != null;
        boolean hasPalettes = sourcePalette != null && targetPalette != null;
        
        analyzeButton.setDisable(!hasSource && !hasTarget);
        resynthesizeButton.setDisable(!hasSource || !hasTarget || !hasPalettes);
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        applyDarkStyleToDialog(alert);
        alert.showAndWait();
    }
    
    private void applyDarkStyleToDialog(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: #252525;" +
            "-fx-font-size: 14px;"
        );
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: #e0e0e0;");
        
        if (dialogPane.lookup(".header-panel") != null) {
            dialogPane.lookup(".header-panel").setStyle("-fx-background-color: #2b2b2b;");
        }
        
        dialogPane.getButtonTypes().forEach(buttonType -> {
            Button button = (Button) dialogPane.lookupButton(buttonType);
            if (button != null) {
                button.setStyle(
                    "-fx-background-color: #3c3c3c; -fx-text-fill: #e0e0e0; " +
                    "-fx-background-radius: 6; -fx-padding: 8 20; -fx-cursor: hand;"
                );
            }
        });
    }
}
