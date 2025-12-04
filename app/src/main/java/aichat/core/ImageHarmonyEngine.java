package aichat.core;

import aichat.algorithm.ClusteringStrategy;
import aichat.algorithm.HybridClusterer;
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
    
    public enum ColorModel {
        RGB, CIELAB
    }
    
    private static final int MAX_PIXELS = 10000;
    private static final int MAX_TILE_PIXELS = 16 * 1024 * 1024;
    private static final long DEFAULT_SEED = 42L;
    
    private final ColorModel colorModel;
    private final ClusteringStrategy clusteringStrategy;
    private final NativeAccelerator nativeAccelerator;
    private final long seed;
    
    public ImageHarmonyEngine() {
        this(ColorModel.RGB, DEFAULT_SEED);
    }
    
    public ImageHarmonyEngine(ColorModel colorModel) {
        this(colorModel, DEFAULT_SEED);
    }
    
    public ImageHarmonyEngine(ColorModel colorModel, long seed) {
        this.colorModel = colorModel;
        this.seed = seed;
        this.clusteringStrategy = new HybridClusterer(seed);
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    public ColorPalette analyze(BufferedImage image, int k) {
        List<ColorPoint> sampledPixels = null;
        
        if (nativeAccelerator.isAvailable()) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] rawPixels = new int[width * height];
            image.getRGB(0, 0, width, height, rawPixels, 0, width);
            sampledPixels = nativeAccelerator.samplePixelsFromImage(rawPixels, MAX_PIXELS, seed);
        }
        
        if (sampledPixels == null) {
            sampledPixels = extractPixels(image, MAX_PIXELS);
        }

        if (k > sampledPixels.size()) {
            k = sampledPixels.size();
        }

        List<ColorPoint> workingPixels = convertColorSpace(sampledPixels, true);
        List<ColorPoint> centroids = clusteringStrategy.cluster(workingPixels, k);
        List<ColorPoint> resultColors = convertColorSpace(centroids, false);

        return new ColorPalette(resultColors);
    }
    
    /**
     * Resynthesize with color transfer - preserves image details.
     * Each pixel's offset from the nearest target palette color is preserved
     * when mapping to the source palette.
     */
    public BufferedImage resynthesize(BufferedImage targetImage, 
                                       ColorPalette sourcePalette, 
                                       ColorPalette targetPalette) {
        return resynthesizeInternal(targetImage, sourcePalette, targetPalette, false);
    }
    
    /**
     * Posterize - replaces each pixel with the exact color from source palette.
     * Result will contain ONLY colors from the source palette (k colors max).
     */
    public BufferedImage posterize(BufferedImage targetImage, 
                                    ColorPalette sourcePalette, 
                                    ColorPalette targetPalette) {
        return resynthesizeInternal(targetImage, sourcePalette, targetPalette, true);
    }
    
    private BufferedImage resynthesizeInternal(BufferedImage targetImage, 
                                                ColorPalette sourcePalette, 
                                                ColorPalette targetPalette,
                                                boolean posterize) {
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
        long totalPixels = (long) width * height;
        
        int[] pixels = new int[width * height];
        targetImage.getRGB(0, 0, width, height, pixels, 0, width);
        
        // Posterize mode - direct palette color replacement
        if (posterize) {
            // Try native posterize first
            if (nativeAccelerator.isAvailable()) {
                int[] result = nativeAccelerator.posterizeImage(
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
            // Fallback to Java
            return posterizeJava(targetImage, mappedSource, targetPalette);
        }
        
        // Try GPU first for large images (>1MP) - much faster
        if (totalPixels > 1_000_000 && nativeAccelerator.hasOpenCL()) {
            int[] result = nativeAccelerator.resynthesizeImageGPU(
                pixels, width, height,
                targetPalette,
                mappedSource
            );
            
            if (result != null) {
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                output.setRGB(0, 0, width, height, result, 0, width);
                return output;
            }
            // Fall through to CPU if GPU failed
        }
        
        if (nativeAccelerator.isAvailable()) {
            // Use tiled processing for very large images to limit memory
            if (totalPixels > MAX_TILE_PIXELS) {
                return resynthesizeTiled(targetImage, mappedSource, targetPalette);
            }
            
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
    
    private BufferedImage resynthesizeTiled(BufferedImage targetImage,
                                             ColorPalette mappedSource,
                                             ColorPalette targetPalette) {
        int width = targetImage.getWidth();
        int height = targetImage.getHeight();
        
        int tileHeight = Math.max(1, MAX_TILE_PIXELS / width);
        tileHeight = Math.min(height, (tileHeight / 64) * 64);
        if (tileHeight == 0) tileHeight = 64;
        
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        int y = 0;
        while (y < height) {
            int currentTileHeight = Math.min(tileHeight, height - y);
            
            int[] tilePixelsArray = new int[width * currentTileHeight];
            targetImage.getRGB(0, y, width, currentTileHeight, tilePixelsArray, 0, width);
            
            int[] resultTile = nativeAccelerator.resynthesizeImage(
                tilePixelsArray, width, currentTileHeight,
                targetPalette, mappedSource
            );
            
            if (resultTile != null) {
                output.setRGB(0, y, width, currentTileHeight, resultTile, 0, width);
            } else {
                for (int ty = 0; ty < currentTileHeight; ty++) {
                    for (int x = 0; x < width; x++) {
                        int idx = ty * width + x;
                        int rgb = tilePixelsArray[idx];
                        ColorPoint pixel = ColorPoint.fromRGB(rgb);
                        
                        int closest = targetPalette.findClosestIndex(pixel);
                        ColorPoint targetCenter = targetPalette.getColor(closest);
                        ColorPoint sourceCenter = mappedSource.getColor(closest);
                        
                        double dc1 = pixel.c1() - targetCenter.c1();
                        double dc2 = pixel.c2() - targetCenter.c2();
                        double dc3 = pixel.c3() - targetCenter.c3();
                        
                        ColorPoint newColor = new ColorPoint(
                            clamp(sourceCenter.c1() + dc1, 0, 255),
                            clamp(sourceCenter.c2() + dc2, 0, 255),
                            clamp(sourceCenter.c3() + dc3, 0, 255)
                        );
                        
                        output.setRGB(x, y + ty, newColor.toRGB());
                    }
                }
            }
            
            y += currentTileHeight;
        }
        
        return output;
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
    
    /**
     * Posterization: replace each pixel with the exact palette color (no offset).
     * Result contains only K distinct colors.
     */
    private BufferedImage posterizeJava(BufferedImage targetImage,
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
                ColorPoint sourceCenter = sourceColors.get(targetIndex);
                
                // Direct replacement - no offset preservation
                result.setRGB(x, y, sourceCenter.toRGB());
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
    
    private List<ColorPoint> extractPixels(BufferedImage image, int maxSamples) {
        int width = image.getWidth();
        int height = image.getHeight();
        long total = (long) width * (long) height;

        if (total <= maxSamples) {
            List<ColorPoint> pixels = new ArrayList<>((int) total);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    pixels.add(ColorPoint.fromRGB(rgb));
                }
            }
            return pixels;
        }

        // Reservoir sampling
        List<ColorPoint> reservoir = new ArrayList<>(maxSamples);
        Random rnd = new Random(seed);
        long seen = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                ColorPoint cp = ColorPoint.fromRGB(rgb);
                if (seen < maxSamples) {
                    reservoir.add(cp);
                } else {
                    long j = Math.abs(rnd.nextLong()) % (seen + 1);
                    if (j < maxSamples) {
                        reservoir.set((int) j, cp);
                    }
                }
                seen++;
            }
        }

        return reservoir;
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
    
    public ColorModel getColorModel() {
        return colorModel;
    }
    
    public String getAlgorithmName() {
        return clusteringStrategy.getName();
    }
}
