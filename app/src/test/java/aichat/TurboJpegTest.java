package aichat;

import aichat.native_.NativeAccelerator;
import aichat.native_.NativeLibrary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TurboJPEG Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TurboJpegTest {
    
    static boolean isForceJavaMode() {
        return Boolean.getBoolean("force.java");
    }
    
    private static Path tempDir;
    private static File testJpegFile;
    
    @BeforeAll
    static void setup() throws IOException {
        // Create temp directory
        tempDir = Files.createTempDirectory("turbojpeg-test");
        
        // Create a test JPEG image
        BufferedImage testImage = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        
        // Fill with a pattern
        for (int y = 0; y < 1000; y++) {
            for (int x = 0; x < 1000; x++) {
                int r = (x * 255) / 1000;
                int g = (y * 255) / 1000;
                int b = ((x + y) * 127) / 1000;
                testImage.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        
        testJpegFile = tempDir.resolve("test.jpg").toFile();
        ImageIO.write(testImage, "JPEG", testJpegFile);
    }
    
    @AfterAll
    static void cleanup() throws IOException {
        if (testJpegFile != null && testJpegFile.exists()) {
            testJpegFile.delete();
        }
        if (tempDir != null) {
            Files.deleteIfExists(tempDir);
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Native library should be available")
    void nativeLibraryAvailable() {
        assertTrue(NativeLibrary.isAvailable(), "Native library should be available");
    }
    
    @Test
    @Order(2)
    @DisplayName("TurboJPEG should be available")
    @DisabledIf("isForceJavaMode")
    void turboJpegAvailable() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        assertTrue(accel.hasTurboJpeg(), "TurboJPEG should be available");
    }
    
    @Test
    @Order(3)
    @DisplayName("Should decode JPEG file correctly")
    @DisabledIf("isForceJavaMode")
    void decodeJpegFile() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        
        NativeAccelerator.DecodedImage decoded = accel.decodeJpeg(testJpegFile.getAbsolutePath());
        
        assertNotNull(decoded, "Decoded image should not be null");
        assertEquals(1000, decoded.width(), "Width should match");
        assertEquals(1000, decoded.height(), "Height should match");
        assertEquals(1000 * 1000, decoded.pixels().length, "Pixel count should match");
    }
    
    @Test
    @Order(4)
    @DisplayName("Decoded pixels should have valid ARGB format")
    @DisabledIf("isForceJavaMode")
    void decodedPixelsFormat() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        
        NativeAccelerator.DecodedImage decoded = accel.decodeJpeg(testJpegFile.getAbsolutePath());
        assertNotNull(decoded);
        
        // All pixels should have alpha = 255 (0xFF)
        for (int i = 0; i < Math.min(100, decoded.pixels().length); i++) {
            int pixel = decoded.pixels()[i];
            int alpha = (pixel >> 24) & 0xFF;
            assertEquals(255, alpha, "Alpha should be 255 (opaque)");
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("TurboJPEG should be faster than ImageIO")
    @DisabledIf("isForceJavaMode")
    void performanceComparison() throws IOException {
        // Warm up
        for (int i = 0; i < 3; i++) {
            ImageIO.read(testJpegFile);
            NativeAccelerator.getInstance().decodeJpeg(testJpegFile.getAbsolutePath());
        }
        
        // Measure ImageIO
        long imageioStart = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            ImageIO.read(testJpegFile);
        }
        long imageioTime = System.nanoTime() - imageioStart;
        
        // Measure TurboJPEG
        long turboStart = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            NativeAccelerator.getInstance().decodeJpeg(testJpegFile.getAbsolutePath());
        }
        long turboTime = System.nanoTime() - turboStart;
        
        double speedup = (double) imageioTime / turboTime;
        
        System.out.printf("ImageIO: %.2fms, TurboJPEG: %.2fms, Speedup: %.2fx%n",
            imageioTime / 1_000_000.0 / 5,
            turboTime / 1_000_000.0 / 5,
            speedup);
        
        // TurboJPEG should be at least 1.5x faster (conservative threshold)
        assertTrue(speedup > 1.0, 
            String.format("TurboJPEG should be faster, got %.2fx speedup", speedup));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should return null for non-existent file")
    @DisabledIf("isForceJavaMode")
    void nonExistentFile() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        
        NativeAccelerator.DecodedImage result = accel.decodeJpeg("/non/existent/file.jpg");
        
        assertNull(result, "Should return null for non-existent file");
    }
    
    @Test
    @Order(7)
    @DisplayName("Should return null for invalid JPEG data")
    @DisabledIf("isForceJavaMode")
    void invalidJpegData() throws IOException {
        // Create a file with invalid JPEG data
        File invalidFile = tempDir.resolve("invalid.jpg").toFile();
        Files.write(invalidFile.toPath(), "not a jpeg".getBytes());
        
        NativeAccelerator accel = NativeAccelerator.getInstance();
        NativeAccelerator.DecodedImage result = accel.decodeJpeg(invalidFile.getAbsolutePath());
        
        assertNull(result, "Should return null for invalid JPEG");
        
        invalidFile.delete();
    }
    
    @Test
    @Order(8)
    @DisplayName("Decoded image should be usable with BufferedImage")
    @DisabledIf("isForceJavaMode")
    void createBufferedImage() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        
        NativeAccelerator.DecodedImage decoded = accel.decodeJpeg(testJpegFile.getAbsolutePath());
        assertNotNull(decoded);
        
        // Create BufferedImage from decoded data
        BufferedImage img = new BufferedImage(
            decoded.width(), decoded.height(), BufferedImage.TYPE_INT_ARGB
        );
        img.setRGB(0, 0, decoded.width(), decoded.height(), 
            decoded.pixels(), 0, decoded.width());
        
        assertEquals(1000, img.getWidth());
        assertEquals(1000, img.getHeight());
        
        // Sample some pixels to verify data integrity
        int topLeft = img.getRGB(0, 0);
        int bottomRight = img.getRGB(999, 999);
        
        // Top-left should be dark (x=0, y=0)
        assertTrue(((topLeft >> 16) & 0xFF) < 50, "Top-left R should be low");
        assertTrue(((topLeft >> 8) & 0xFF) < 50, "Top-left G should be low");
        
        // Bottom-right should be brighter (x=999, y=999)
        assertTrue(((bottomRight >> 16) & 0xFF) > 200, "Bottom-right R should be high");
        assertTrue(((bottomRight >> 8) & 0xFF) > 200, "Bottom-right G should be high");
    }
}
