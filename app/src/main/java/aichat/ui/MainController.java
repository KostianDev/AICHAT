package aichat.ui;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.Algorithm;
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
    
    @FXML private SplitPane mainSplitPane;
    
    @FXML private ComboBox<Algorithm> algorithmCombo;
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
        algorithmCombo.getItems().addAll(Algorithm.values());
        algorithmCombo.setValue(Algorithm.KMEANS);
        
        colorModelCombo.getItems().addAll(ColorModel.values());
        colorModelCombo.setValue(ColorModel.RGB);
        
        SpinnerValueFactory<Integer> kFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 32, 5);
        kSpinner.setValueFactory(kFactory);
        
        // Bind image views to scroll pane size for responsive scaling
        sourceImageView.fitWidthProperty().bind(
            mainSplitPane.widthProperty().multiply(0.45));
        targetImageView.fitWidthProperty().bind(
            mainSplitPane.widthProperty().multiply(0.45));
        
        updateButtonStates();
        statusLabel.setText("Ready. Load images to begin.");
        progressBar.setProgress(0);
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
        
        Algorithm algorithm = algorithmCombo.getValue();
        ColorModel colorModel = colorModelCombo.getValue();
        int k = kSpinner.getValue();
        
        setProcessing(true, "Analyzing images...");
        
        Task<Void> analyzeTask = new Task<>() {
            private ColorPalette srcPal;
            private ColorPalette tgtPal;
            
            @Override
            protected Void call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(algorithm, colorModel);
                
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
        
        Algorithm algorithm = algorithmCombo.getValue();
        ColorModel colorModel = colorModelCombo.getValue();
        
        setProcessing(true, "Resynthesizing image...");
        
        // Capture palettes for the task
        final ColorPalette srcPal = sourcePalette;
        final ColorPalette tgtPal = targetPalette;
        
        Task<BufferedImage> resynthTask = new Task<>() {
            @Override
            protected BufferedImage call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(algorithm, colorModel);
                // Apply source palette colors to target image
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
        imageView.setFitWidth(Math.min(image.getWidth(), 1200));
        imageView.setFitHeight(Math.min(image.getHeight(), 800));
        
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #2b2b2b;");
        
        Button saveButton = new Button("Save Image");
        saveButton.setOnAction(e -> handleSaveResult());
        
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> resultStage.close());
        
        HBox buttonBar = new HBox(10, saveButton, closeButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(10));
        buttonBar.setStyle("-fx-background-color: #f0f0f0;");
        
        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(buttonBar);
        
        Scene scene = new Scene(root, 
            Math.min(image.getWidth() + 40, 1240), 
            Math.min(image.getHeight() + 80, 880));
        
        resultStage.setScene(scene);
        resultStage.show();
        resultStage.toFront();
    }
    
    private void handleSaveResult() {
        if (resultImage == null) {
            showAlert("No Result", "No result image to save.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Result Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG Files", "*.png"),
            new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg")
        );
        
        File file = fileChooser.showSaveDialog(resultStage);
        
        if (file != null) {
            try {
                String format = file.getName().toLowerCase().endsWith(".png") ? "PNG" : "JPEG";
                ImageIO.write(resultImage, format, file);
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
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                showAlert("Load Error", "Could not read image file.");
                return;
            }
            
            if (isSource) {
                sourceImage = image;
                sourcePalette = null;  // Reset palette when new image loaded
                sourcePalettePane.getChildren().clear();
                displayImage(sourceImageView, image);
            } else {
                targetImage = image;
                targetPalette = null;  // Reset palette when new image loaded
                targetPalettePane.getChildren().clear();
                displayImage(targetImageView, image);
            }
            
            statusLabel.setText("Loaded: " + file.getName() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            updateButtonStates();
            
        } catch (IOException e) {
            showAlert("Load Error", "Failed to load image: " + e.getMessage());
        }
    }
    
    private void displayImage(ImageView imageView, BufferedImage image) {
        Image fxImage = SwingFXUtils.toFXImage(image, null);
        imageView.setImage(fxImage);
    }
    
    private void displayPalette(FlowPane pane, ColorPalette palette) {
        pane.getChildren().clear();
        if (palette == null) return;
        
        for (ColorPoint color : palette.getColors()) {
            VBox colorBox = new VBox(2);
            colorBox.setStyle("-fx-alignment: center;");
            
            Rectangle rect = new Rectangle(45, 45);
            rect.setFill(Color.rgb(
                clamp((int) color.c1()),
                clamp((int) color.c2()),
                clamp((int) color.c3())
            ));
            rect.setStroke(Color.GRAY);
            rect.setStrokeWidth(1);
            rect.setArcWidth(5);
            rect.setArcHeight(5);
            
            Label hexLabel = new Label(color.toHexString());
            hexLabel.setStyle("-fx-font-size: 10px; -fx-font-family: monospace;");
            
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
        alert.showAndWait();
    }
}
