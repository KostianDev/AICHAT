package aichat.native_;

import aichat.model.ColorPalette;
import aichat.model.ColorPoint;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

public final class NativeAccelerator {
    
    private static final NativeAccelerator INSTANCE = new NativeAccelerator();
    
    private final NativeLibrary nativeLib;
    private final boolean available;
    
    private NativeAccelerator() {
        this.nativeLib = NativeLibrary.getInstance();
        this.available = NativeLibrary.isAvailable();
        
        if (available) {
            System.out.println("Native acceleration enabled: " + nativeLib.getVersion() 
                + " (SIMD: " + nativeLib.hasSIMD() + ")");
        } else {
            System.out.println("Native acceleration unavailable, using Java fallback");
        }
    }
    
    public static NativeAccelerator getInstance() {
        return INSTANCE;
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public String getVersion() {
        return available ? nativeLib.getVersion() : "Java fallback";
    }
    
    public boolean hasSIMD() {
        return available && nativeLib.hasSIMD();
    }
    
    public List<ColorPoint> kmeansCluster(List<ColorPoint> points, int k, 
                                           int maxIterations, double threshold, long seed) {
        if (!available || points.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] flatPoints = colorPointsToFloatArray(points);
            float[] result = nativeLib.kmeansCluster(arena, flatPoints, k, 
                maxIterations, (float) threshold, seed);
            return floatArrayToColorPoints(result);
        } catch (Exception e) {
            System.err.println("Native K-Means failed: " + e.getMessage());
            return null;
        }
    }
    
    public List<ColorPoint> rgbToLabBatch(List<ColorPoint> rgb) {
        if (!available || rgb.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] flatRgb = colorPointsToFloatArray(rgb);
            float[] result = nativeLib.rgbToLabBatch(arena, flatRgb);
            return floatArrayToColorPoints(result);
        } catch (Exception e) {
            System.err.println("Native RGB to LAB failed: " + e.getMessage());
            return null;
        }
    }
    
    public List<ColorPoint> labToRgbBatch(List<ColorPoint> lab) {
        if (!available || lab.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] flatLab = colorPointsToFloatArray(lab);
            float[] result = nativeLib.labToRgbBatch(arena, flatLab);
            return floatArrayToColorPoints(result);
        } catch (Exception e) {
            System.err.println("Native LAB to RGB failed: " + e.getMessage());
            return null;
        }
    }
    
    public int[] resynthesizeImage(int[] pixels, int width, int height,
                                    ColorPalette targetPalette, ColorPalette sourcePalette) {
        if (!available || pixels.length == 0) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] target = colorPaletteToFloatArray(targetPalette);
            float[] source = colorPaletteToFloatArray(sourcePalette);
            return nativeLib.resynthesizeImage(arena, pixels, width, height, target, source);
        } catch (Exception e) {
            System.err.println("Native resynthesis failed: " + e.getMessage());
            return null;
        }
    }
    
    public List<ColorPoint> samplePixels(List<ColorPoint> pixels, int sampleSize, long seed) {
        if (!available || pixels.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] input = colorPointsToFloatArray(pixels);
            float[] result = nativeLib.samplePixels(arena, input, sampleSize, seed);
            return floatArrayToColorPoints(result);
        } catch (Exception e) {
            System.err.println("Native sampling failed: " + e.getMessage());
            return null;
        }
    }
    
    public int[] assignPointsBatch(List<ColorPoint> points, List<ColorPoint> centroids) {
        if (!available || points.isEmpty() || centroids.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] flatPoints = colorPointsToFloatArray(points);
            float[] flatCentroids = colorPointsToFloatArray(centroids);
            return nativeLib.assignPointsBatch(arena, flatPoints, flatCentroids);
        } catch (Exception e) {
            System.err.println("Native assignment failed: " + e.getMessage());
            return null;
        }
    }
    
    public record DbscanResult(int numClusters, int[] labels, List<ColorPoint> centroids) {}
    
    public List<ColorPoint> hybridCluster(List<ColorPoint> points, int k, 
                                           int blockSize, int minPts, long seed) {
        if (!available || points.isEmpty()) {
            return null;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            float[] flatPoints = colorPointsToFloatArray(points);
            
            float eps = nativeLib.hybridCalculateEps(arena, flatPoints, blockSize, minPts, seed);
            
            float[] result = nativeLib.hybridCluster(
                arena, flatPoints, k, blockSize, eps, minPts,
                100, 0.5f, seed
            );
            
            return floatArrayToColorPoints(result);
        } catch (Exception e) {
            System.err.println("Native Hybrid clustering failed: " + e.getMessage());
            return null;
        }
    }
    
    private static float[] colorPointsToFloatArray(List<ColorPoint> points) {
        float[] result = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            ColorPoint p = points.get(i);
            result[i * 3] = (float) p.c1();
            result[i * 3 + 1] = (float) p.c2();
            result[i * 3 + 2] = (float) p.c3();
        }
        return result;
    }
    
    private static List<ColorPoint> floatArrayToColorPoints(float[] flat) {
        List<ColorPoint> result = new ArrayList<>(flat.length / 3);
        for (int i = 0; i < flat.length; i += 3) {
            result.add(new ColorPoint(flat[i], flat[i + 1], flat[i + 2]));
        }
        return result;
    }
    
    private static float[] colorPaletteToFloatArray(ColorPalette palette) {
        List<ColorPoint> colors = palette.getColors();
        return colorPointsToFloatArray(colors);
    }
}
