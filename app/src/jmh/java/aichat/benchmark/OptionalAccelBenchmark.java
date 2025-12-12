package aichat.benchmark;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.native_.NativeAccelerator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** JMH Benchmark: OpenCL GPU and TurboJPEG optional accelerations. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class OptionalAccelBenchmark {

    @Param({"1000"})
    private int imageSize;

    @Param({"16"})
    private int clusterCount;

    private BufferedImage sourceImage;
    private BufferedImage targetImage;
    private ColorPalette sourcePalette;
    private ColorPalette targetPalette;
    private int[] targetPixels;
    private NativeAccelerator accel;
    private ImageHarmonyEngine engine;
    
    private File tempJpegFile;
    private byte[] jpegBytes;
    private boolean openclAvailable;
    private boolean turboJpegAvailable;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        accel = NativeAccelerator.getInstance();
        engine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
        
        openclAvailable = accel.hasOpenCL();
        turboJpegAvailable = accel.hasTurboJpeg();
        
        System.out.printf("[Setup] ImageSize: %dx%d, OpenCL: %s, TurboJPEG: %s%n",
            imageSize, imageSize, openclAvailable, turboJpegAvailable);
        
        sourceImage = generateTestImage(imageSize, imageSize, 1L);
        targetImage = generateTestImage(imageSize, imageSize, 2L);
        
        sourcePalette = engine.analyze(sourceImage, clusterCount);
        targetPalette = engine.analyze(targetImage, clusterCount);
        
        int width = targetImage.getWidth();
        int height = targetImage.getHeight();
        targetPixels = new int[width * height];
        targetImage.getRGB(0, 0, width, height, targetPixels, 0, width);
        
        // Prepare JPEG test data
        tempJpegFile = File.createTempFile("benchmark", ".jpg");
        tempJpegFile.deleteOnExit();
        ImageIO.write(sourceImage, "jpg", tempJpegFile);
        jpegBytes = Files.readAllBytes(tempJpegFile.toPath());
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (tempJpegFile != null && tempJpegFile.exists()) {
            tempJpegFile.delete();
        }
    }

    @Benchmark
    public void resynthesis_CPU(Blackhole bh) {
        if (!accel.isAvailable()) {
            bh.consume(0);
            return;
        }
        int[] result = accel.resynthesizeImage(
            targetPixels, imageSize, imageSize,
            targetPalette, sourcePalette
        );
        bh.consume(result);
    }

    @Benchmark
    public void resynthesis_GPU(Blackhole bh) {
        if (!openclAvailable) {
            bh.consume(0);
            return;
        }
        int[] result = accel.resynthesizeImageGPU(
            targetPixels, imageSize, imageSize,
            targetPalette, sourcePalette
        );
        bh.consume(result);
    }

    @Benchmark
    public void jpegDecode_ImageIO(Blackhole bh) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
        bh.consume(img);
    }

    @Benchmark
    public void jpegDecode_TurboJPEG(Blackhole bh) {
        if (!turboJpegAvailable) {
            bh.consume(0);
            return;
        }
        NativeAccelerator.DecodedImage result = accel.decodeJpeg(tempJpegFile.getAbsolutePath());
        bh.consume(result);
    }

    @Benchmark
    public void jpegEncode_ImageIO(Blackhole bh) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(sourceImage, "jpg", baos);
        bh.consume(baos.toByteArray());
    }

    @Benchmark
    public void jpegEncode_TurboJPEG(Blackhole bh) throws IOException {
        if (!turboJpegAvailable) {
            bh.consume(0);
            return;
        }
        File outFile = File.createTempFile("bench_out", ".jpg");
        outFile.deleteOnExit();
        boolean success = accel.saveJpeg(sourceImage, 90, outFile.getAbsolutePath());
        bh.consume(success);
        outFile.delete();
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Dforce.java=true"})
    public void fullResynthesis_Java(Blackhole bh) {
        bh.consume(engine.resynthesize(targetImage, sourcePalette, targetPalette));
    }

    @Benchmark
    public void fullResynthesis_NativeCPU(Blackhole bh) {
        // Native auto-selects CPU for small images
        BufferedImage smallTarget = generateTestImage(500, 500, 3L);
        ColorPalette tgtPal = engine.analyze(smallTarget, clusterCount);
        bh.consume(engine.resynthesize(smallTarget, sourcePalette, tgtPal));
    }

    @Benchmark
    public void fullResynthesis_NativeGPU(Blackhole bh) {
        if (!openclAvailable) {
            bh.consume(0);
            return;
        }
        // Force GPU by using large image (>1MP triggers GPU path)
        BufferedImage largeTarget = generateTestImage(1100, 1100, 4L);
        ColorPalette tgtPal = engine.analyze(largeTarget, clusterCount);
        bh.consume(engine.resynthesize(largeTarget, sourcePalette, tgtPal));
    }

    private static BufferedImage generateTestImage(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        
        int numBlobs = 5 + random.nextInt(4);
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
                
                int noise = random.nextInt(16) - 8;
                int finalR = clamp((int)(r / totalWeight) + noise);
                int finalG = clamp((int)(g / totalWeight) + noise);
                int finalB = clamp((int)(b / totalWeight) + noise);
                
                image.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
            }
        }
        
        return image;
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
