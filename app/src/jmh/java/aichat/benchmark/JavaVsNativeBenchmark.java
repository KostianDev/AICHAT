package aichat.benchmark;

import aichat.algorithm.HybridClusterer;
import aichat.color.ColorSpaceConverter;
import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** JMH Benchmark: Java fallback vs Native acceleration. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class JavaVsNativeBenchmark {

    @Param({"10000"})
    private int dataSize;

    @Param({"16"})
    private int clusterCount;

    private List<ColorPoint> testPoints;
    private BufferedImage testImage;
    private HybridClusterer clusterer;
    private ImageHarmonyEngine engine;
    private String backend;

    @Setup(Level.Trial)
    public void setup() {
        testPoints = generateRandomPoints(dataSize, 42L);
        testImage = generateTestImage(200, 200, 42L);
        clusterer = new HybridClusterer(42L);
        engine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
        
        NativeAccelerator accel = NativeAccelerator.getInstance();
        backend = accel.isAvailable() ? "NATIVE" : "JAVA";
        System.out.printf("[Setup] Backend: %s, DataSize: %d, Clusters: %d%n", 
            backend, dataSize, clusterCount);
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Dforce.java=true"})
    public void colorConversion_Java(Blackhole bh) {
        bh.consume(ColorSpaceConverter.rgbToLabBatch(testPoints));
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Dforce.java=true"})
    public void clustering_Java(Blackhole bh) {
        bh.consume(clusterer.cluster(testPoints, clusterCount));
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Dforce.java=true"})
    public void analyze_Java(Blackhole bh) {
        bh.consume(engine.analyze(testImage, clusterCount));
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Dforce.java=true"})
    public void fullPipeline_Java(Blackhole bh) {
        BufferedImage source = generateTestImage(100, 100, 1L);
        BufferedImage target = generateTestImage(100, 100, 2L);
        
        ColorPalette srcPalette = engine.analyze(source, clusterCount);
        ColorPalette tgtPalette = engine.analyze(target, clusterCount);
        
        bh.consume(engine.resynthesize(target, srcPalette, tgtPalette));
    }

    @Benchmark
    @Fork(value = 1)
    public void colorConversion_Native(Blackhole bh) {
        bh.consume(ColorSpaceConverter.rgbToLabBatch(testPoints));
    }

    @Benchmark
    @Fork(value = 1)
    public void clustering_Native(Blackhole bh) {
        bh.consume(clusterer.cluster(testPoints, clusterCount));
    }

    @Benchmark
    @Fork(value = 1)
    public void analyze_Native(Blackhole bh) {
        bh.consume(engine.analyze(testImage, clusterCount));
    }

    @Benchmark
    @Fork(value = 1)
    public void fullPipeline_Native(Blackhole bh) {
        BufferedImage source = generateTestImage(100, 100, 1L);
        BufferedImage target = generateTestImage(100, 100, 2L);
        
        ColorPalette srcPalette = engine.analyze(source, clusterCount);
        ColorPalette tgtPalette = engine.analyze(target, clusterCount);
        
        bh.consume(engine.resynthesize(target, srcPalette, tgtPalette));
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
        int[] blobX = new int[numBlobs];
        int[] blobY = new int[numBlobs];
        int[][] blobColor = new int[numBlobs][3];
        
        for (int i = 0; i < numBlobs; i++) {
            blobX[i] = random.nextInt(width);
            blobY[i] = random.nextInt(height);
            blobColor[i][0] = random.nextInt(256);
            blobColor[i][1] = random.nextInt(256);
            blobColor[i][2] = random.nextInt(256);
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int nearest = 0;
                double minDist = Double.MAX_VALUE;
                for (int i = 0; i < numBlobs; i++) {
                    double dist = Math.hypot(x - blobX[i], y - blobY[i]);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = i;
                    }
                }
                
                int r = clamp(blobColor[nearest][0] + random.nextInt(30) - 15);
                int g = clamp(blobColor[nearest][1] + random.nextInt(30) - 15);
                int b = clamp(blobColor[nearest][2] + random.nextInt(30) - 15);
                
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        
        return image;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
