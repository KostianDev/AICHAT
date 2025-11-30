package aichat.benchmark;

import aichat.algorithm.HybridClusterer;
import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for high color count (k=256, 512) scenarios.
 * 
 * Run with: ./gradlew jmh -Pjmh.include="HighColorCountBenchmark"
 * 
 * Measures:
 * 1. Clustering performance with high k values (256, 512)
 * 2. Resynthesis with/without LUT optimization (k <= 256 uses LUT)
 * 3. Scalability: how performance degrades with increasing k
 * 4. Image size impact at high color counts
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
public class HighColorCountBenchmark {

    // High color count parameters
    @Param({"64", "128", "256", "512"})
    private int colorCount;

    // Image size (pixels per side for square image)
    @Param({"1000", "2000"})
    private int imageSize;

    private BufferedImage sourceImage;
    private BufferedImage targetImage;
    private List<ColorPoint> colorPoints;
    private HybridClusterer clusterer;
    private ImageHarmonyEngine rgbEngine;
    private ImageHarmonyEngine labEngine;

    @Setup(Level.Trial)
    public void setup() {
        int numPixels = imageSize * imageSize;
        
        // Generate test data
        sourceImage = generateRealisticImage(imageSize, imageSize, 1L);
        targetImage = generateRealisticImage(imageSize, imageSize, 2L);
        colorPoints = extractColorPoints(sourceImage);
        
        clusterer = new HybridClusterer(42L);
        rgbEngine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        labEngine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
        
        System.out.printf("[Setup] Image: %dx%d (%d pixels), k=%d%n", 
            imageSize, imageSize, numPixels, colorCount);
    }

    // ==================== Clustering Benchmarks ====================

    @Benchmark
    public void clusteringHighK(Blackhole bh) {
        bh.consume(clusterer.cluster(colorPoints, colorCount));
    }

    // ==================== Analysis Benchmarks ====================

    @Benchmark
    public void analyzeRgbHighK(Blackhole bh) {
        bh.consume(rgbEngine.analyze(sourceImage, colorCount));
    }

    @Benchmark
    public void analyzeCielabHighK(Blackhole bh) {
        bh.consume(labEngine.analyze(sourceImage, colorCount));
    }

    // ==================== Resynthesis Benchmarks ====================

    @Benchmark
    public void resynthesisRgbHighK(Blackhole bh) {
        ColorPalette srcPalette = rgbEngine.analyze(sourceImage, colorCount);
        ColorPalette tgtPalette = rgbEngine.analyze(targetImage, colorCount);
        bh.consume(rgbEngine.resynthesize(targetImage, srcPalette, tgtPalette));
    }

    @Benchmark  
    public void resynthesisCielabHighK(Blackhole bh) {
        ColorPalette srcPalette = labEngine.analyze(sourceImage, colorCount);
        ColorPalette tgtPalette = labEngine.analyze(targetImage, colorCount);
        bh.consume(labEngine.resynthesize(targetImage, srcPalette, tgtPalette));
    }

    // ==================== Full Pipeline Benchmark ====================

    @Benchmark
    public void fullPipelineRgbHighK(Blackhole bh) {
        // Complete workflow: analyze both + resynthesize
        ColorPalette srcPalette = rgbEngine.analyze(sourceImage, colorCount);
        ColorPalette tgtPalette = rgbEngine.analyze(targetImage, colorCount);
        BufferedImage result = rgbEngine.resynthesize(targetImage, srcPalette, tgtPalette);
        bh.consume(result);
    }

    // ==================== Helper Methods ====================

    private static BufferedImage generateRealisticImage(int width, int height, long seed) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        
        // Create 5-8 color blobs for realistic color distribution
        int numBlobs = 5 + rnd.nextInt(4);
        int[] blobX = new int[numBlobs];
        int[] blobY = new int[numBlobs];
        int[][] blobColor = new int[numBlobs][3];
        
        for (int i = 0; i < numBlobs; i++) {
            blobX[i] = rnd.nextInt(width);
            blobY[i] = rnd.nextInt(height);
            blobColor[i][0] = rnd.nextInt(256);
            blobColor[i][1] = rnd.nextInt(256);
            blobColor[i][2] = rnd.nextInt(256);
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double totalWeight = 0;
                double r = 0, g = 0, b = 0;
                
                for (int i = 0; i < numBlobs; i++) {
                    double dx = x - blobX[i];
                    double dy = y - blobY[i];
                    double dist = Math.sqrt(dx * dx + dy * dy) + 1;
                    double weight = 1.0 / (dist * dist);
                    
                    r += blobColor[i][0] * weight;
                    g += blobColor[i][1] * weight;
                    b += blobColor[i][2] * weight;
                    totalWeight += weight;
                }
                
                int noise = rnd.nextInt(16) - 8;
                int finalR = clamp((int)(r / totalWeight) + noise);
                int finalG = clamp((int)(g / totalWeight) + noise);
                int finalB = clamp((int)(b / totalWeight) + noise);
                
                img.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
            }
        }
        
        return img;
    }

    private static List<ColorPoint> extractColorPoints(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<ColorPoint> points = new ArrayList<>(width * height);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                points.add(new ColorPoint(r, g, b));
            }
        }
        
        return points;
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
