package aichat.core;

import aichat.algorithm.ClusteringStrategy;
import aichat.algorithm.KMeansClusterer;
import aichat.algorithm.DbscanClusterer;
import aichat.color.ColorSpaceConverter;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Main orchestrator for image analysis and resynthesis.
 * Uses Strategy Pattern for clustering algorithm selection.
 */
public class ImageHarmonyEngine {
    
    public enum Algorithm {
        KMEANS, DBSCAN
    }
    
    public enum ColorModel {
        RGB, CIELAB
    }
    
    private static final int MAX_PIXELS_KMEANS = 50000;
    private static final int MAX_PIXELS_DBSCAN = 5000;
    
    private final Algorithm algorithm;
    private final ColorModel colorModel;
    private final ClusteringStrategy clusteringStrategy;
    
    public ImageHarmonyEngine(Algorithm algorithm, ColorModel colorModel) {
        this.algorithm = algorithm;
        this.colorModel = colorModel;
        this.clusteringStrategy = createStrategy(algorithm);
    }
    
    private ClusteringStrategy createStrategy(Algorithm algorithm) {
        return switch (algorithm) {
            case KMEANS -> new KMeansClusterer();
            case DBSCAN -> new DbscanClusterer();
        };
    }
    
    /**
     * Analyzes an image and extracts the dominant color palette.
     */
    public ColorPalette analyze(BufferedImage image, int k) {
        List<ColorPoint> pixels = extractPixels(image);
        
        int maxPixels = (algorithm == Algorithm.DBSCAN) ? MAX_PIXELS_DBSCAN : MAX_PIXELS_KMEANS;
        List<ColorPoint> sampledPixels = samplePixels(pixels, maxPixels);
        
        List<ColorPoint> workingPixels = convertColorSpace(sampledPixels, true);
        List<ColorPoint> centroids = clusteringStrategy.cluster(workingPixels, k);
        List<ColorPoint> resultColors = convertColorSpace(centroids, false);
        
        return new ColorPalette(resultColors);
    }
    
    /**
     * Resynthesizes the source image using the color palette from target image.
     */
    public BufferedImage resynthesize(BufferedImage sourceImage, BufferedImage targetImage, int k) {
        ColorPalette targetPalette = analyze(targetImage, k);
        ColorPalette sourcePalette = analyze(sourceImage, k);
        
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = sourceImage.getRGB(x, y);
                ColorPoint sourcePixel = ColorPoint.fromRGB(rgb);
                
                int sourceIndex = sourcePalette.findClosestIndex(sourcePixel);
                ColorPoint targetColor = targetPalette.getColors().get(
                    sourceIndex % targetPalette.getColors().size()
                );
                
                result.setRGB(x, y, targetColor.toRGB());
            }
        }
        
        return result;
    }
    
    private List<ColorPoint> extractPixels(BufferedImage image) {
        List<ColorPoint> pixels = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                pixels.add(ColorPoint.fromRGB(rgb));
            }
        }
        
        return pixels;
    }
    
    private List<ColorPoint> samplePixels(List<ColorPoint> pixels, int maxSize) {
        if (pixels.size() <= maxSize) {
            return pixels;
        }
        
        List<ColorPoint> sampled = new ArrayList<>(pixels);
        Collections.shuffle(sampled, new Random(42));
        return sampled.subList(0, maxSize);
    }
    
    private List<ColorPoint> convertColorSpace(List<ColorPoint> points, boolean toLab) {
        if (colorModel == ColorModel.RGB) {
            return points;
        }
        
        List<ColorPoint> converted = new ArrayList<>(points.size());
        for (ColorPoint point : points) {
            if (toLab) {
                converted.add(ColorSpaceConverter.rgbToLab(point));
            } else {
                converted.add(ColorSpaceConverter.labToRgb(point));
            }
        }
        return converted;
    }
    
    public Algorithm getAlgorithm() {
        return algorithm;
    }
    
    public ColorModel getColorModel() {
        return colorModel;
    }
}
