package aichat.benchmark;

import aichat.algorithm.HybridClusterer;
import aichat.color.ColorSpaceConverter;
import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** JMH Benchmark: AICHAT core components performance. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class AichatBenchmark {

    @Param({"5000", "10000"})
    private int dataSize;

    @Param({"8", "16"})
    private int clusterCount;

    private List<ColorPoint> testPoints;
    private BufferedImage testImage;
    private HybridClusterer clusterer;
    private ImageHarmonyEngine rgbEngine;
    private ImageHarmonyEngine labEngine;

    @Setup(Level.Trial)
    public void setup() {
        testPoints = generateRandomPoints(dataSize, 42L);
        testImage = generateTestImage(100, 100, 42L);
        clusterer = new HybridClusterer(42L);
        rgbEngine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        labEngine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
    }

    @Benchmark
    public void rgbToLabSingle(Blackhole bh) {
        for (ColorPoint p : testPoints) {
            bh.consume(ColorSpaceConverter.rgbToLab(p));
        }
    }

    @Benchmark
    public void rgbToLabBatch(Blackhole bh) {
        bh.consume(ColorSpaceConverter.rgbToLabBatch(testPoints));
    }

    @Benchmark
    public void labToRgbSingle(Blackhole bh) {
        // First convert to LAB, then back
        List<ColorPoint> labPoints = ColorSpaceConverter.rgbToLabBatch(testPoints);
        for (ColorPoint p : labPoints) {
            bh.consume(ColorSpaceConverter.labToRgb(p));
        }
    }

    @Benchmark
    public void labToRgbBatch(Blackhole bh) {
        List<ColorPoint> labPoints = ColorSpaceConverter.rgbToLabBatch(testPoints);
        bh.consume(ColorSpaceConverter.labToRgbBatch(labPoints));
    }

    @Benchmark
    public void hybridClustering(Blackhole bh) {
        bh.consume(clusterer.cluster(testPoints, clusterCount));
    }

    @Benchmark
    public void analyzeRgb(Blackhole bh) {
        bh.consume(rgbEngine.analyze(testImage, clusterCount));
    }

    @Benchmark
    public void analyzeCielab(Blackhole bh) {
        bh.consume(labEngine.analyze(testImage, clusterCount));
    }

    @Benchmark
    public void fullPipelineRgb(Blackhole bh) {
        BufferedImage source = generateTestImage(50, 50, 1L);
        BufferedImage target = generateTestImage(50, 50, 2L);
        
        ColorPalette srcPalette = rgbEngine.analyze(source, clusterCount);
        ColorPalette tgtPalette = rgbEngine.analyze(target, clusterCount);
        
        bh.consume(rgbEngine.resynthesize(target, srcPalette, tgtPalette));
    }

    @Benchmark
    public void fullPipelineCielab(Blackhole bh) {
        BufferedImage source = generateTestImage(50, 50, 1L);
        BufferedImage target = generateTestImage(50, 50, 2L);
        
        ColorPalette srcPalette = labEngine.analyze(source, clusterCount);
        ColorPalette tgtPalette = labEngine.analyze(target, clusterCount);
        
        bh.consume(labEngine.resynthesize(target, srcPalette, tgtPalette));
    }

    @Benchmark
    public void deltaE2000Calculation(Blackhole bh) {
        List<ColorPoint> labPoints = ColorSpaceConverter.rgbToLabBatch(testPoints);
        
        // Compare each point with the next
        for (int i = 0; i < labPoints.size() - 1; i++) {
            bh.consume(ColorSpaceConverter.deltaE2000(labPoints.get(i), labPoints.get(i + 1)));
        }
    }

    private static List<ColorPoint> generateRandomPoints(int count, long seed) {
        List<ColorPoint> points = new ArrayList<>(count);
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            points.add(new ColorPoint(
                random.nextDouble() * 255,
                random.nextDouble() * 255,
                random.nextDouble() * 255
            ));
        }
        return points;
    }

    private static BufferedImage generateTestImage(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        
        int numBlobs = 5;
        Color[] blobColors = new Color[numBlobs];
        int[] blobX = new int[numBlobs];
        int[] blobY = new int[numBlobs];
        
        for (int i = 0; i < numBlobs; i++) {
            blobColors[i] = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            blobX[i] = random.nextInt(width);
            blobY[i] = random.nextInt(height);
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int nearest = 0;
                double minDist = Double.MAX_VALUE;
                for (int i = 0; i < numBlobs; i++) {
                    double dist = Math.sqrt((x - blobX[i]) * (x - blobX[i]) + 
                                           (y - blobY[i]) * (y - blobY[i]));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = i;
                    }
                }
                
                // Add some noise
                Color base = blobColors[nearest];
                int r = clamp(base.getRed() + random.nextInt(30) - 15);
                int g = clamp(base.getGreen() + random.nextInt(30) - 15);
                int b = clamp(base.getBlue() + random.nextInt(30) - 15);
                
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        
        return image;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
