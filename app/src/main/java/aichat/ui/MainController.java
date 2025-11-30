package aichat.ui;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.export.PaletteExporter;
import aichat.export.PaletteExporter.ExportFormat;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

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
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

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
    @FXML private Button swapButton;
    
    @FXML private Button exportSourceOptimalBtn;
    @FXML private Button exportSourcePngBtn;
    @FXML private Button exportTargetOptimalBtn;
    @FXML private Button exportTargetPngBtn;
    
    @FXML private FlowPane sourcePalettePane;
    @FXML private FlowPane targetPalettePane;
    
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    
    private BufferedImage sourceImage;
    private BufferedImage targetImage;
    private BufferedImage resultImage;
    
    // Loading state tracking
    private volatile boolean sourceLoading = false;
    private volatile boolean targetLoading = false;
    private volatile long sourceLoadId = 0;  // To track which load operation is current
    private volatile long targetLoadId = 0;
    
    private ColorPalette sourcePalette;
    private ColorPalette targetPalette;
    
    private Stage resultStage;
    
    @FXML
    public void initialize() {
        colorModelCombo.getItems().addAll(ColorModel.values());
        colorModelCombo.setValue(ColorModel.RGB);
        
        colorModelCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateExportButtonLabels();
        });
        
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
    private void handleSwap() {
        // Don't swap if images are still loading
        if (sourceLoading || targetLoading) {
            showAlert("Please Wait", "Images are still loading. Please wait before swapping.");
            return;
        }
        
        if (sourceImage == null || targetImage == null) {
            showAlert("Cannot Swap", "Both source and target images must be loaded.");
            return;
        }
        
        // Swap BufferedImages
        BufferedImage tempImage = sourceImage;
        sourceImage = targetImage;
        targetImage = tempImage;
        
        // Swap palettes
        ColorPalette tempPalette = sourcePalette;
        sourcePalette = targetPalette;
        targetPalette = tempPalette;
        
        // Update displays from BufferedImages (ensures sync)
        sourceImageView.setImage(SwingFXUtils.toFXImage(sourceImage, null));
        targetImageView.setImage(SwingFXUtils.toFXImage(targetImage, null));
        
        displayPalette(sourcePalettePane, sourcePalette);
        displayPalette(targetPalettePane, targetPalette);
        
        updateButtonStates();
        statusLabel.setText("Swapped source and target.");
    }
    
    @FXML
    private void handleAnalyze() {
        if (sourceLoading || targetLoading) {
            showAlert("Please Wait", "Images are still loading. Please wait before analyzing.");
            return;
        }
        
        if (sourceImage == null && targetImage == null) {
            showAlert("No Image", "Please load at least one image to analyze.");
            return;
        }
        
        ColorModel colorModel = colorModelCombo.getValue();
        int k = kSpinner.getValue();
        
        runAnalysis(colorModel, k);
    }
    
    private void runAnalysis(ColorModel colorModel, int k) {
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
        
        // JPEG is much faster for large images
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JPEG Image (fast)", "*.jpg", "*.jpeg"),
            new FileChooser.ExtensionFilter("PNG Image (lossless)", "*.png")
        );
        fileChooser.setInitialFileName("result.jpg");
        
        File file = fileChooser.showSaveDialog(resultStage);
        
        if (file != null) {
            String path = file.getAbsolutePath().toLowerCase();
            boolean isJpeg = path.endsWith(".jpg") || path.endsWith(".jpeg");
            
            // Add extension if missing
            if (!path.endsWith(".jpg") && !path.endsWith(".jpeg") && !path.endsWith(".png")) {
                FileChooser.ExtensionFilter selectedFilter = fileChooser.getSelectedExtensionFilter();
                if (selectedFilter != null && selectedFilter.getDescription().contains("JPEG")) {
                    file = new File(file.getAbsolutePath() + ".jpg");
                    isJpeg = true;
                } else {
                    file = new File(file.getAbsolutePath() + ".png");
                    isJpeg = false;
                }
            }
            
            try {
                long start = System.currentTimeMillis();
                
                if (isJpeg) {
                    // Try fast TurboJPEG first
                    NativeAccelerator nativeAccel = NativeAccelerator.getInstance();
                    if (nativeAccel.hasTurboJpeg()) {
                        if (nativeAccel.saveJpeg(resultImage, 90, file.getAbsolutePath())) {
                            long elapsed = System.currentTimeMillis() - start;
                            statusLabel.setText(String.format("Image saved: %s (%dms, TurboJPEG)", 
                                file.getName(), elapsed));
                            return;
                        }
                    }
                    // Fallback to ImageIO for JPEG
                    ImageIO.write(resultImage, "JPEG", file);
                } else {
                    ImageIO.write(resultImage, "PNG", file);
                }
                
                long elapsed = System.currentTimeMillis() - start;
                statusLabel.setText(String.format("Image saved: %s (%dms)", file.getName(), elapsed));
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
        // Mark as loading and invalidate current image
        if (isSource) {
            sourceLoading = true;
            sourceImage = null;
            sourcePalette = null;
            sourceLoadId++;
            sourcePalettePane.getChildren().clear();
        } else {
            targetLoading = true;
            targetImage = null;
            targetPalette = null;
            targetLoadId++;
            targetPalettePane.getChildren().clear();
        }
        
        updateButtonStates();
        statusLabel.setText("Loading: " + file.getName() + "...");
        progressBar.setProgress(-1); // Indeterminate
        
        // Capture load ID to detect if a newer load was started
        final long currentLoadId = isSource ? sourceLoadId : targetLoadId;
        
        // Step 1: Show preview immediately using JavaFX async loading (fast, downscaled)
        String url = file.toURI().toString();
        Image preview = new Image(url, 1200, 1200, true, true, true); // Async, scaled
        
        preview.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 1.0 && !preview.isError()) {
                // Show preview while full image loads
                long activeLoadId = isSource ? sourceLoadId : targetLoadId;
                if (currentLoadId == activeLoadId) {
                    if (isSource) {
                        sourceImageView.setImage(preview);
                    } else {
                        targetImageView.setImage(preview);
                    }
                    statusLabel.setText("Loading full image: " + file.getName() + "...");
                }
            }
        });
        
        // Step 2: Load full BufferedImage in background with optimizations
        Task<BufferedImage> loadTask = new Task<>() {
            @Override
            protected BufferedImage call() throws IOException {
                return readImageOptimized(file);
            }
            
            @Override
            protected void succeeded() {
                // Check if this load is still current
                long activeLoadId = isSource ? sourceLoadId : targetLoadId;
                if (currentLoadId != activeLoadId) {
                    return;
                }
                
                BufferedImage img = getValue();
                if (img == null) {
                    showAlert("Load Error", "Could not read image file.");
                    if (isSource) {
                        sourceLoading = false;
                    } else {
                        targetLoading = false;
                    }
                    updateButtonStates();
                    progressBar.setProgress(0);
                    statusLabel.setText("Ready.");
                    return;
                }
                
                if (isSource) {
                    sourceImage = img;
                    sourceLoading = false;
                } else {
                    targetImage = img;
                    targetLoading = false;
                }
                
                updateButtonStates();
                progressBar.setProgress(0);
                statusLabel.setText("Loaded: " + file.getName() + 
                    " (" + img.getWidth() + "x" + img.getHeight() + ")");
            }
            
            @Override
            protected void failed() {
                long activeLoadId = isSource ? sourceLoadId : targetLoadId;
                if (currentLoadId != activeLoadId) {
                    return;
                }
                
                showAlert("Load Error", "Failed to load image: " + getException().getMessage());
                if (isSource) {
                    sourceLoading = false;
                } else {
                    targetLoading = false;
                }
                updateButtonStates();
                progressBar.setProgress(0);
                statusLabel.setText("Ready.");
            }
        };
        
        new Thread(loadTask).start();
    }
    
    //Optimized image reading using TurboJPEG for JPEGs.
    private BufferedImage readImageOptimized(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            NativeAccelerator accel = NativeAccelerator.getInstance();
            if (accel.hasTurboJpeg()) {
                NativeAccelerator.DecodedImage decoded = accel.decodeJpeg(file.getAbsolutePath());
                if (decoded != null) {
                    BufferedImage img = new BufferedImage(
                        decoded.width(), decoded.height(), BufferedImage.TYPE_INT_ARGB
                    );
                    img.setRGB(0, 0, decoded.width(), decoded.height(), 
                        decoded.pixels(), 0, decoded.width());
                    return img;
                }
                // TurboJPEG failed, fall through to standard path
            }
        }
        
        // Get appropriate reader
        Iterator<ImageReader> readers;
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            readers = ImageIO.getImageReadersByFormatName("JPEG");
        } else if (fileName.endsWith(".png")) {
            readers = ImageIO.getImageReadersByFormatName("PNG");
        } else {
            // Fallback to standard ImageIO
            return ImageIO.read(file);
        }
        
        if (!readers.hasNext()) {
            return ImageIO.read(file);
        }
        
        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            reader.setInput(iis, true, true); // seekForwardOnly=true, ignoreMetadata=true
            
            ImageReadParam param = reader.getDefaultReadParam();
            
            return reader.read(0, param);
        } finally {
            reader.dispose();
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
        boolean hasSource = sourceImage != null && !sourceLoading;
        boolean hasTarget = targetImage != null && !targetLoading;
        boolean isLoading = sourceLoading || targetLoading;
        boolean hasPalettes = sourcePalette != null && targetPalette != null;
        boolean hasSourcePalette = sourcePalette != null;
        boolean hasTargetPalette = targetPalette != null;
        
        // Disable analyze if still loading or no images
        analyzeButton.setDisable(isLoading || (!hasSource && !hasTarget));
        resynthesizeButton.setDisable(isLoading || !hasSource || !hasTarget || !hasPalettes);
        
        // Disable swap if loading or missing images
        swapButton.setDisable(isLoading || !hasSource || !hasTarget);
        
        exportSourceOptimalBtn.setDisable(!hasSourcePalette);
        exportSourcePngBtn.setDisable(!hasSourcePalette);
        exportTargetOptimalBtn.setDisable(!hasTargetPalette);
        exportTargetPngBtn.setDisable(!hasTargetPalette);
        
        updateExportButtonLabels();
    }
    
    private void updateExportButtonLabels() {
        ColorModel model = colorModelCombo.getValue();
        String formatDesc = PaletteExporter.getOptimalFormatDescription(model);
        String shortFormat = model == ColorModel.RGB ? "GPL" : "CSV";
        
        exportSourceOptimalBtn.setText(shortFormat);
        exportTargetOptimalBtn.setText(shortFormat);
        
        exportSourceOptimalBtn.setTooltip(new Tooltip("Export as " + formatDesc));
        exportTargetOptimalBtn.setTooltip(new Tooltip("Export as " + formatDesc));
        exportSourcePngBtn.setTooltip(new Tooltip("Export as PNG image with color squares"));
        exportTargetPngBtn.setTooltip(new Tooltip("Export as PNG image with color squares"));
    }
    
    @FXML
    private void handleExportSourceOptimal() {
        exportPalette(sourcePalette, ExportFormat.OPTIMAL, "source");
    }
    
    @FXML
    private void handleExportSourcePng() {
        exportPalette(sourcePalette, ExportFormat.PNG_IMAGE, "source");
    }
    
    @FXML
    private void handleExportTargetOptimal() {
        exportPalette(targetPalette, ExportFormat.OPTIMAL, "target");
    }
    
    @FXML
    private void handleExportTargetPng() {
        exportPalette(targetPalette, ExportFormat.PNG_IMAGE, "target");
    }
    
    private void exportPalette(ColorPalette palette, ExportFormat format, String paletteName) {
        if (palette == null) {
            showAlert("No Palette", "Please analyze the " + paletteName + " image first.");
            return;
        }
        
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Export Directory");
        
        Stage stage = (Stage) loadSourceButton.getScene().getWindow();
        File directory = chooser.showDialog(stage);
        
        if (directory != null) {
            try {
                ColorModel model = colorModelCombo.getValue();
                File exportedFile = PaletteExporter.exportPalette(palette, model, format, directory);
                statusLabel.setText("Exported: " + exportedFile.getName());
            } catch (IOException e) {
                showAlert("Export Error", "Failed to export palette: " + e.getMessage());
            }
        }
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
