package aichat.native_;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Optional;

public final class NativeLibrary {
    
    private static final NativeLibrary INSTANCE = new NativeLibrary();
    private static final boolean AVAILABLE;
    
    private final SymbolLookup library;
    private final Linker linker;
    
    private final MethodHandle kmeans_cluster;
    private final MethodHandle assign_points_batch;
    private final MethodHandle rgb_to_lab_batch;
    private final MethodHandle lab_to_rgb_batch;
    private final MethodHandle resynthesize_image;
    private final MethodHandle extract_pixels;
    private final MethodHandle sample_pixels;
    private final MethodHandle aichat_native_version;
    private final MethodHandle aichat_has_simd;
    private final MethodHandle hybrid_cluster;
    private final MethodHandle hybrid_calculate_dbscan_eps;
    
    public static final StructLayout COLOR_POINT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_FLOAT.withName("c1"),
        ValueLayout.JAVA_FLOAT.withName("c2"),
        ValueLayout.JAVA_FLOAT.withName("c3")
    ).withName("ColorPoint3f");
    
    static {
        boolean available = false;
        
        try {
            NativeLibrary lib = INSTANCE;
            if (lib.library != null) {
                available = true;
            }
        } catch (Throwable t) {
            // Ignore
        }
        
        AVAILABLE = available;
    }
    
    private NativeLibrary() {
        this.linker = Linker.nativeLinker();
        this.library = loadLibrary();
        
        if (this.library != null) {
            this.kmeans_cluster = lookupFunction("kmeans_cluster",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG
                ));
            
            this.assign_points_batch = lookupFunction("assign_points_batch",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
                ));
            
            this.rgb_to_lab_batch = lookupFunction("rgb_to_lab_batch",
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                ));
            
            this.lab_to_rgb_batch = lookupFunction("lab_to_rgb_batch",
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                ));
            
            this.resynthesize_image = lookupFunction("resynthesize_image",
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
                ));
            
            this.extract_pixels = lookupFunction("extract_pixels",
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
                ));
            
            this.sample_pixels = lookupFunction("sample_pixels",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG
                ));
            
            this.aichat_native_version = lookupFunction("aichat_native_version",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
            
            this.aichat_has_simd = lookupFunction("aichat_has_simd",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            
            this.hybrid_cluster = lookupFunction("hybrid_cluster",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG
                ));
            
            this.hybrid_calculate_dbscan_eps = lookupFunction("hybrid_calculate_dbscan_eps",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG
                ));
        } else {
            this.kmeans_cluster = null;
            this.assign_points_batch = null;
            this.rgb_to_lab_batch = null;
            this.lab_to_rgb_batch = null;
            this.resynthesize_image = null;
            this.extract_pixels = null;
            this.sample_pixels = null;
            this.aichat_native_version = null;
            this.aichat_has_simd = null;
            this.hybrid_cluster = null;
            this.hybrid_calculate_dbscan_eps = null;
        }
    }
    
    private SymbolLookup loadLibrary() {
        String osName = System.getProperty("os.name").toLowerCase();
        String libName;
        String platform;
        
        if (osName.contains("linux")) {
            libName = "libaichat_native.so";
            platform = "linux";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            libName = "libaichat_native.dylib";
            platform = "macos";
        } else if (osName.contains("win")) {
            libName = "aichat_native.dll";
            platform = "windows";
        } else {
            System.err.println("Unsupported OS: " + osName);
            return null;
        }
        
        try {
            String resourcePath = "/native/" + platform + "/" + libName;
            var url = getClass().getResource(resourcePath);
            if (url != null) {
                Path libPath = Path.of(url.toURI());
                return SymbolLookup.libraryLookup(libPath, Arena.global());
            }
        } catch (Exception e) {
            System.err.println("Failed to load from resources: " + e.getMessage());
        }
        
        try {
            return SymbolLookup.libraryLookup(libName, Arena.global());
        } catch (Exception e) {
            System.err.println("Failed to load from library path: " + e.getMessage());
        }
        
        try {
            String projectDir = System.getProperty("user.dir");
            Path libPath = Path.of(projectDir, "native", "build", libName);
            if (libPath.toFile().exists()) {
                return SymbolLookup.libraryLookup(libPath, Arena.global());
            }
        } catch (Exception e) {
            System.err.println("Failed to load from project dir: " + e.getMessage());
        }
        
        System.err.println("Native library not found. Using Java fallback.");
        return null;
    }
    
    private MethodHandle lookupFunction(String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = library.find(name);
        if (symbol.isPresent()) {
            return linker.downcallHandle(symbol.get(), descriptor);
        } else {
            System.err.println("Function not found: " + name);
            return null;
        }
    }
    
    public static NativeLibrary getInstance() {
        return INSTANCE;
    }
    
    public static boolean isAvailable() {
        return AVAILABLE;
    }
    
    public String getVersion() {
        if (aichat_native_version == null) return "N/A (fallback)";
        try {
            MemorySegment ptr = (MemorySegment) aichat_native_version.invokeExact();
            return ptr.reinterpret(256).getString(0);
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }
    
    public boolean hasSIMD() {
        if (aichat_has_simd == null) return false;
        try {
            return ((int) aichat_has_simd.invokeExact()) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public float[] kmeansCluster(Arena arena, float[] points, int k, 
                                  int maxIterations, float threshold, long seed) {
        if (kmeans_cluster == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = points.length / 3;
        
        MemorySegment pointsNative = arena.allocate(ValueLayout.JAVA_FLOAT, points.length);
        MemorySegment centroidsNative = arena.allocate(COLOR_POINT_LAYOUT, k);
        MemorySegment assignmentsNative = arena.allocate(ValueLayout.JAVA_INT, n);
        
        pointsNative.copyFrom(MemorySegment.ofArray(points));
        
        try {
            int iterations = (int) kmeans_cluster.invokeExact(
                pointsNative, n, k, maxIterations, threshold,
                centroidsNative, assignmentsNative, seed
            );
            
            float[] result = new float[k * 3];
            for (int i = 0; i < k; i++) {
                long offset = i * COLOR_POINT_LAYOUT.byteSize();
                result[i * 3] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset);
                result[i * 3 + 1] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset + 4);
                result[i * 3 + 2] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset + 8);
            }
            
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("K-Means native call failed", t);
        }
    }
    
    public float[] rgbToLabBatch(Arena arena, float[] rgb) {
        if (rgb_to_lab_batch == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = rgb.length / 3;
        
        MemorySegment rgbNative = arena.allocate(ValueLayout.JAVA_FLOAT, rgb.length);
        MemorySegment labNative = arena.allocate(ValueLayout.JAVA_FLOAT, rgb.length);
        
        rgbNative.copyFrom(MemorySegment.ofArray(rgb));
        
        try {
            rgb_to_lab_batch.invokeExact(rgbNative, labNative, n);
            
            float[] result = new float[rgb.length];
            MemorySegment.ofArray(result).copyFrom(labNative);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("RGB to LAB native call failed", t);
        }
    }
    
    public float[] labToRgbBatch(Arena arena, float[] lab) {
        if (lab_to_rgb_batch == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = lab.length / 3;
        
        MemorySegment labNative = arena.allocate(ValueLayout.JAVA_FLOAT, lab.length);
        MemorySegment rgbNative = arena.allocate(ValueLayout.JAVA_FLOAT, lab.length);
        
        labNative.copyFrom(MemorySegment.ofArray(lab));
        
        try {
            lab_to_rgb_batch.invokeExact(labNative, rgbNative, n);
            
            float[] result = new float[lab.length];
            MemorySegment.ofArray(result).copyFrom(rgbNative);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("LAB to RGB native call failed", t);
        }
    }
    
    public int[] resynthesizeImage(Arena arena, int[] imagePixels, int width, int height,
                                    float[] targetPalette, float[] sourcePalette) {
        if (resynthesize_image == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int paletteSize = sourcePalette.length / 3;
        int n = width * height;
        
        MemorySegment imageNative = arena.allocate(ValueLayout.JAVA_INT, n);
        MemorySegment targetPaletteNative = arena.allocate(ValueLayout.JAVA_FLOAT, targetPalette.length);
        MemorySegment sourcePaletteNative = arena.allocate(ValueLayout.JAVA_FLOAT, sourcePalette.length);
        MemorySegment outputNative = arena.allocate(ValueLayout.JAVA_INT, n);
        
        imageNative.copyFrom(MemorySegment.ofArray(imagePixels));
        targetPaletteNative.copyFrom(MemorySegment.ofArray(targetPalette));
        sourcePaletteNative.copyFrom(MemorySegment.ofArray(sourcePalette));
        
        try {
            resynthesize_image.invokeExact(
                imageNative, width, height,
                targetPaletteNative, sourcePaletteNative, paletteSize, outputNative
            );
            
            int[] result = new int[n];
            MemorySegment.ofArray(result).copyFrom(outputNative);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Resynthesize native call failed", t);
        }
    }
    
    public float[] samplePixels(Arena arena, float[] input, int sampleSize, long seed) {
        if (sample_pixels == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int inputSize = input.length / 3;
        int maxSamples = Math.min(inputSize, sampleSize);
        
        MemorySegment inputNative = arena.allocate(ValueLayout.JAVA_FLOAT, input.length);
        MemorySegment outputNative = arena.allocate(ValueLayout.JAVA_FLOAT, maxSamples * 3);
        
        inputNative.copyFrom(MemorySegment.ofArray(input));
        
        try {
            int actualSize = (int) sample_pixels.invokeExact(
                inputNative, inputSize, outputNative, sampleSize, seed
            );
            
            float[] result = new float[actualSize * 3];
            MemorySegment.ofArray(result).copyFrom(outputNative.asSlice(0, result.length * 4));
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Sample pixels native call failed", t);
        }
    }
    
    public int[] assignPointsBatch(Arena arena, float[] points, float[] centroids) {
        if (assign_points_batch == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = points.length / 3;
        int k = centroids.length / 3;
        
        MemorySegment pointsNative = arena.allocate(ValueLayout.JAVA_FLOAT, points.length);
        MemorySegment centroidsNative = arena.allocate(ValueLayout.JAVA_FLOAT, centroids.length);
        MemorySegment assignmentsNative = arena.allocate(ValueLayout.JAVA_INT, n);
        
        pointsNative.copyFrom(MemorySegment.ofArray(points));
        centroidsNative.copyFrom(MemorySegment.ofArray(centroids));
        
        for (int i = 0; i < n; i++) {
            assignmentsNative.setAtIndex(ValueLayout.JAVA_INT, i, -1);
        }
        
        try {
            assign_points_batch.invokeExact(pointsNative, n, centroidsNative, k, assignmentsNative);
            
            int[] result = new int[n];
            MemorySegment.ofArray(result).copyFrom(assignmentsNative);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Assign points native call failed", t);
        }
    }
    
    public record DbscanResult(int numClusters, int[] labels, float[] centroids) {}
    
    public float[] hybridCluster(Arena arena, float[] points, int k, int blockSize, 
                                  float dbscanEps, int dbscanMinPts, 
                                  int kmeansMaxIter, float kmeansThreshold, long seed) {
        if (hybrid_cluster == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = points.length / 3;
        
        MemorySegment pointsNative = arena.allocate(ValueLayout.JAVA_FLOAT, points.length);
        MemorySegment centroidsNative = arena.allocate(COLOR_POINT_LAYOUT, k);
        
        pointsNative.copyFrom(MemorySegment.ofArray(points));
        
        try {
            int iterations = (int) hybrid_cluster.invokeExact(
                pointsNative, n, k, blockSize, dbscanEps, dbscanMinPts,
                kmeansMaxIter, kmeansThreshold, centroidsNative, seed
            );
            
            float[] result = new float[k * 3];
            for (int i = 0; i < k; i++) {
                long offset = i * COLOR_POINT_LAYOUT.byteSize();
                result[i * 3] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset);
                result[i * 3 + 1] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset + 4);
                result[i * 3 + 2] = centroidsNative.get(ValueLayout.JAVA_FLOAT, offset + 8);
            }
            
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Hybrid cluster native call failed", t);
        }
    }
    
    public float hybridCalculateEps(Arena arena, float[] points, int blockSize, int minPts, long seed) {
        if (hybrid_calculate_dbscan_eps == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int n = points.length / 3;
        
        MemorySegment pointsNative = arena.allocate(ValueLayout.JAVA_FLOAT, points.length);
        pointsNative.copyFrom(MemorySegment.ofArray(points));
        
        try {
            return (float) hybrid_calculate_dbscan_eps.invokeExact(
                pointsNative, n, blockSize, minPts, seed
            );
        } catch (Throwable t) {
            throw new RuntimeException("Hybrid calculate eps failed", t);
        }
    }
}
