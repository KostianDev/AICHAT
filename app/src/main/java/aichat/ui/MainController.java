package aichat.ui;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.Algorithm;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Main controller for the AICHAT JavaFX application.
 */
public class MainController {
    
    @FXML private ImageView sourceImageView;
    @FXML private ImageView targetImageView;
    @FXML private ImageView resultImageView;
    
    @FXML private ComboBox<Algorithm> algorithmCombo;
    @FXML private ComboBox<ColorModel> colorModelCombo;
    @FXML private Spinner<Integer> kSpinner;
    
    @FXML private Button loadSourceButton;
    @FXML private Button loadTargetButton;
    @FXML private Button analyzeButton;
    @FXML private Button resynthesizeButton;
    @FXML private Button saveResultButton;
    
    @FXML private FlowPane sourcePalettePane;
    @FXML private FlowPane targetPalettePane;
    
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    
    private BufferedImage sourceImage;
    private BufferedImage targetImage;
    private BufferedImage resultImage;
    
    @FXML
    public void initialize() {
        algorithmCombo.getItems().addAll(Algorithm.values());
        algorithmCombo.setValue(Algorithm.KMEANS);
        
        colorModelCombo.getItems().addAll(ColorModel.values());
        colorModelCombo.setValue(ColorModel.RGB);
        
        SpinnerValueFactory<Integer> kFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 32, 5);
        kSpinner.setValueFactory(kFactory);
        
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
            private ColorPalette srcPalette;
            private ColorPalette tgtPalette;
            
            @Override
            protected Void call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(algorithm, colorModel);
                
                if (sourceImage != null) {
                    srcPalette = engine.analyze(sourceImage, k);
                }
                if (targetImage != null) {
                    tgtPalette = engine.analyze(targetImage, k);
                }
                return null;
            }
            
            @Override
            protected void succeeded() {
                displayPalette(sourcePalettePane, srcPalette);
                displayPalette(targetPalettePane, tgtPalette);
                setProcessing(false, "Analysis complete.");
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
        
        Algorithm algorithm = algorithmCombo.getValue();
        ColorModel colorModel = colorModelCombo.getValue();
        int k = kSpinner.getValue();
        
        setProcessing(true, "Resynthesizing image...");
        
        Task<BufferedImage> resynthTask = new Task<>() {
            @Override
            protected BufferedImage call() {
                ImageHarmonyEngine engine = new ImageHarmonyEngine(algorithm, colorModel);
                return engine.resynthesize(sourceImage, targetImage, k);
            }
            
            @Override
            protected void succeeded() {
                resultImage = getValue();
                displayImage(resultImageView, resultImage);
                setProcessing(false, "Resynthesis complete.");
                updateButtonStates();
            }
            
            @Override
            protected void failed() {
                setProcessing(false, "Resynthesis failed: " + getException().getMessage());
            }
        };
        
        new Thread(resynthTask).start();
    }
    
    @FXML
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
        
        Stage stage = (Stage) saveResultButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        
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
                displayImage(sourceImageView, image);
            } else {
                targetImage = image;
                displayImage(targetImageView, image);
            }
            
            statusLabel.setText("Loaded: " + file.getName());
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
            
            Rectangle rect = new Rectangle(40, 40);
            rect.setFill(Color.rgb(
                clamp((int) color.c1()),
                clamp((int) color.c2()),
                clamp((int) color.c3())
            ));
            rect.setStroke(Color.BLACK);
            
            Label hexLabel = new Label(color.toHexString());
            hexLabel.setStyle("-fx-font-size: 9px;");
            
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
        boolean hasResult = resultImage != null;
        
        analyzeButton.setDisable(!hasSource && !hasTarget);
        resynthesizeButton.setDisable(!hasSource || !hasTarget);
        saveResultButton.setDisable(!hasResult);
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
