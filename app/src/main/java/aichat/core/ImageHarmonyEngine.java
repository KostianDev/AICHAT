package aichat.core;

import aichat.algorithm.ClusteringStrategy;
import aichat.algorithm.KMeansClusterer;
import aichat.algorithm.DbscanClusterer;
import aichat.color.ColorSpaceConverter;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ImageHarmonyEngine {
    
    public enum Algorithm {
        KMEANS, DBSCAN
    }
    
    public enum ColorModel {
        RGB, CIELAB
    }
    
    private static final int MAX_PIXELS_KMEANS = 50000;
    private static final int MAX_PIXELS_DBSCAN = 5000;
    private static final long DEFAULT_SEED = 42L;
    
    private final Algorithm algorithm;
    private final ColorModel colorModel;
    private final ClusteringStrategy clusteringStrategy;
    private final NativeAccelerator nativeAccelerator;
    private final long seed;
    
    public ImageHarmonyEngine(Algorithm algorithm, ColorModel colorModel) {
        this(algorithm, colorModel, DEFAULT_SEED);
    }
    
    public ImageHarmonyEngine(Algorithm algorithm, ColorModel colorModel, long seed) {
        this.algorithm = algorithm;
        this.colorModel = colorModel;
        this.seed = seed;
        this.clusteringStrategy = createStrategy(algorithm, seed);
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    private ClusteringStrategy createStrategy(Algorithm algorithm, long seed) {
        return switch (algorithm) {
            case KMEANS -> new KMeansClusterer(seed);
            case DBSCAN -> new DbscanClusterer();
        };
    }
    
    public ColorPalette analyze(BufferedImage image, int k) {
        int maxPixels = (algorithm == Algorithm.DBSCAN) ? MAX_PIXELS_DBSCAN : MAX_PIXELS_KMEANS;
        
        List<ColorPoint> pixels = extractPixels(image);
        List<ColorPoint> sampledPixels;
        
        if (nativeAccelerator.isAvailable()) {
            sampledPixels = nativeAccelerator.samplePixels(pixels, maxPixels, seed);
            if (sampledPixels == null) {
                sampledPixels = samplePixels(pixels, maxPixels);
            }
        } else {
            sampledPixels = samplePixels(pixels, maxPixels);
        }
        
        List<ColorPoint> workingPixels = convertColorSpace(sampledPixels, true);
        List<ColorPoint> centroids = clusteringStrategy.cluster(workingPixels, k);
        List<ColorPoint> resultColors = convertColorSpace(centroids, false);
        
        return new ColorPalette(resultColors);
    }
    
    public BufferedImage resynthesize(BufferedImage targetImage, 
                                       ColorPalette sourcePalette, 
                                       ColorPalette targetPalette) {
        int[] mapping = targetPalette.computeMappingTo(sourcePalette);
        
        List<ColorPoint> targetColors = targetPalette.getColors();
        List<ColorPoint> sourceColors = sourcePalette.getColors();
        
        ColorPalette mappedSource = new ColorPalette(
            java.util.stream.IntStream.range(0, targetColors.size())
                .mapToObj(i -> sourceColors.get(mapping[i]))
                .toList()
        );
        
        int width = targetImage.getWidth();
        int height = targetImage.getHeight();
        
        if (nativeAccelerator.isAvailable()) {
            int[] pixels = new int[width * height];
            targetImage.getRGB(0, 0, width, height, pixels, 0, width);
            
            int[] result = nativeAccelerator.resynthesizeImage(
                pixels, width, height,
                targetPalette,
                mappedSource
            );
            
            if (result != null) {
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                output.setRGB(0, 0, width, height, result, 0, width);
                return output;
            }
        }
        
        return resynthesizeJava(targetImage, mappedSource, targetPalette);
    }
    
    private BufferedImage resynthesizeJava(BufferedImage targetImage,
                                            ColorPalette mappedSource,
                                            ColorPalette targetPalette) {
        List<ColorPoint> sourceColors = mappedSource.getColors();
        List<ColorPoint> targetColors = targetPalette.getColors();
        
        int width = targetImage.getWidth();
        int height = targetImage.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = targetImage.getRGB(x, y);
                ColorPoint pixel = ColorPoint.fromRGB(rgb);
                
                int targetIndex = findClosestIndex(pixel, targetColors);
                ColorPoint targetCenter = targetColors.get(targetIndex);
                ColorPoint sourceCenter = sourceColors.get(targetIndex);
                
                double dc1 = pixel.c1() - targetCenter.c1();
                double dc2 = pixel.c2() - targetCenter.c2();
                double dc3 = pixel.c3() - targetCenter.c3();
                
                ColorPoint newColor = new ColorPoint(
                    clamp(sourceCenter.c1() + dc1, 0, 255),
                    clamp(sourceCenter.c2() + dc2, 0, 255),
                    clamp(sourceCenter.c3() + dc3, 0, 255)
                );
                
                result.setRGB(x, y, newColor.toRGB());
            }
        }
        
        return result;
    }
    
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private int findClosestIndex(ColorPoint pixel, List<ColorPoint> palette) {
        int closestIndex = 0;
        double minDist = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.size(); i++) {
            double dist = pixel.distanceTo(palette.get(i));
            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }
        
        return closestIndex;
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
        Collections.shuffle(sampled, new Random(seed));
        return sampled.subList(0, maxSize);
    }
    
    private List<ColorPoint> convertColorSpace(List<ColorPoint> points, boolean toLab) {
        if (colorModel == ColorModel.RGB) {
            return points;
        }
        
        // Use batch conversion (native-accelerated if available)
        if (toLab) {
            return ColorSpaceConverter.rgbToLabBatch(points);
        } else {
            return ColorSpaceConverter.labToRgbBatch(points);
        }
    }
    
    public Algorithm getAlgorithm() {
        return algorithm;
    }
    
    public ColorModel getColorModel() {
        return colorModel;
    }
}
