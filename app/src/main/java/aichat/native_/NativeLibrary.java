package aichat.native_;

import java.io.InputStream;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class NativeLibrary {
    
    private static final NativeLibrary INSTANCE = new NativeLibrary();
    private static final boolean AVAILABLE;
    
    private final SymbolLookup library;
    private final Linker linker;
    
    private final MethodHandle kmeans_cluster;
    private final MethodHandle assign_points_batch;
    private final MethodHandle distance_squared;
    private final MethodHandle rgb_to_lab_batch;
    private final MethodHandle lab_to_rgb_batch;
    private final MethodHandle resynthesize_image;
    private final MethodHandle posterize_image;
    private final MethodHandle sample_pixels;
    private final MethodHandle aichat_native_version;
    private final MethodHandle aichat_has_simd;
    private final MethodHandle hybrid_cluster;
    private final MethodHandle hybrid_calculate_dbscan_eps;
    private final MethodHandle sample_pixels_from_image;
    private final MethodHandle decode_jpeg_file_turbojpeg;
    private final MethodHandle turbojpeg_decode_buffer;
    private final MethodHandle turbojpeg_free;
    private final MethodHandle turbojpeg_encode_to_file;
    private final MethodHandle aichat_has_turbojpeg;
    
    // OpenCL GPU acceleration
    private final MethodHandle aichat_has_opencl;
    private final MethodHandle opencl_init;
    private final MethodHandle opencl_cleanup;
    private final MethodHandle opencl_get_device_name;
    private final MethodHandle opencl_resynthesize_image;
    private final MethodHandle opencl_resynthesize_streaming;
    
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
            
            this.distance_squared = lookupFunction("distance_squared",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS,
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
            
            this.posterize_image = lookupFunction("posterize_image",
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
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
            
            this.sample_pixels_from_image = lookupFunction("sample_pixels_from_image",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG
                ));
            
            this.decode_jpeg_file_turbojpeg = lookupFunction("decode_jpeg_file_turbojpeg",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                ));
            
            this.turbojpeg_decode_buffer = lookupFunction("turbojpeg_decode_buffer",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // jpeg_data
                    ValueLayout.JAVA_LONG, // jpeg_size
                    ValueLayout.ADDRESS,  // out_width
                    ValueLayout.ADDRESS,  // out_height
                    ValueLayout.ADDRESS   // out_pixels
                ));
            
            this.turbojpeg_free = lookupFunction("turbojpeg_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            
            this.turbojpeg_encode_to_file = lookupFunction("turbojpeg_encode_to_file",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
                ));
            
            this.aichat_has_turbojpeg = lookupFunction("aichat_has_turbojpeg",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            
            // OpenCL GPU acceleration functions
            this.aichat_has_opencl = lookupFunction("aichat_has_opencl",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            
            this.opencl_init = lookupFunction("opencl_init",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            
            this.opencl_cleanup = lookupFunction("opencl_cleanup",
                FunctionDescriptor.ofVoid());
            
            this.opencl_get_device_name = lookupFunction("opencl_get_device_name",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
            
            this.opencl_resynthesize_image = lookupFunction("opencl_resynthesize_image",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // image_pixels
                    ValueLayout.JAVA_INT,  // width
                    ValueLayout.JAVA_INT,  // height
                    ValueLayout.ADDRESS,  // target_palette
                    ValueLayout.ADDRESS,  // source_palette
                    ValueLayout.JAVA_INT,  // palette_size
                    ValueLayout.ADDRESS   // output_pixels
                ));
            
            this.opencl_resynthesize_streaming = lookupFunction("opencl_resynthesize_streaming",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // image_pixels
                    ValueLayout.JAVA_INT,  // width
                    ValueLayout.JAVA_INT,  // height
                    ValueLayout.ADDRESS,  // target_palette
                    ValueLayout.ADDRESS,  // source_palette
                    ValueLayout.JAVA_INT,  // palette_size
                    ValueLayout.ADDRESS,  // output_pixels
                    ValueLayout.JAVA_INT   // tile_height
                ));
        } else {
            this.kmeans_cluster = null;
            this.assign_points_batch = null;
            this.distance_squared = null;
            this.rgb_to_lab_batch = null;
            this.lab_to_rgb_batch = null;
            this.resynthesize_image = null;
            this.posterize_image = null;
            this.sample_pixels = null;
            this.aichat_native_version = null;
            this.aichat_has_simd = null;
            this.hybrid_cluster = null;
            this.hybrid_calculate_dbscan_eps = null;
            this.sample_pixels_from_image = null;
            this.decode_jpeg_file_turbojpeg = null;
            this.turbojpeg_decode_buffer = null;
            this.turbojpeg_free = null;
            this.turbojpeg_encode_to_file = null;
            this.aichat_has_turbojpeg = null;
            this.aichat_has_opencl = null;
            this.opencl_init = null;
            this.opencl_cleanup = null;
            this.opencl_get_device_name = null;
            this.opencl_resynthesize_image = null;
            this.opencl_resynthesize_streaming = null;
        }
    }
    
    private SymbolLookup loadLibrary() {
        String osName = System.getProperty("os.name").toLowerCase();
        String platform;
        String libExtension;
        
        if (osName.contains("linux")) {
            libExtension = ".so";
            platform = "linux";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            libExtension = ".dylib";
            platform = "macos";
        } else if (osName.contains("win")) {
            libExtension = ".dll";
            platform = "windows";
        } else {
            System.err.println("Unsupported OS: " + osName);
            return null;
        }
        
        // Check for variant (for testing different optimization levels)
        // Valid variants: "scalar", "simd", "openmp"
        String variant = System.getProperty("native.variant");
        String libName;
        if (variant != null && !variant.isEmpty()) {
            // Load variant library (e.g., libaichat_native_scalar.so)
            if (platform.equals("windows")) {
                libName = "aichat_native_" + variant + libExtension;
            } else {
                libName = "libaichat_native_" + variant + libExtension;
            }
            System.out.println("Loading native library variant: " + variant);
        } else {
            // Default library name
            if (platform.equals("windows")) {
                libName = "aichat_native" + libExtension;
            } else {
                libName = "libaichat_native" + libExtension;
            }
        }
        
        // Try java.library.path first (set by launcher script)
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath != null) {
            for (String dir : libraryPath.split(java.io.File.pathSeparator)) {
                try {
                    Path libPath = Path.of(dir, libName);
                    if (libPath.toFile().exists()) {
                        return SymbolLookup.libraryLookup(libPath, Arena.global());
                    }
                } catch (Exception e) {
                    // Continue to next path
                }
            }
        }
        
        // Try loading from resources (extract to temp if inside JAR)
        try {
            String resourcePath = "/native/" + platform + "/" + libName;
            var url = getClass().getResource(resourcePath);
            if (url != null) {
                if (url.getProtocol().equals("file")) {
                    // Direct file access (IDE mode)
                    Path libPath = Path.of(url.toURI());
                    return SymbolLookup.libraryLookup(libPath, Arena.global());
                } else {
                    // Inside JAR - extract to temp directory
                    Path tempLib = extractLibraryFromJar(resourcePath, libName);
                    if (tempLib != null) {
                        return SymbolLookup.libraryLookup(tempLib, Arena.global());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load from resources: " + e.getMessage());
        }
        
        // Try default library lookup
        try {
            return SymbolLookup.libraryLookup(libName, Arena.global());
        } catch (Exception e) {
            System.err.println("Failed to load from library path: " + e.getMessage());
        }
        
        // Try project directory (development mode)
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
    
    private Path extractLibraryFromJar(String resourcePath, String libName) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            
            // Create temp directory for native libs (survives between runs)
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "aichat-native");
            Files.createDirectories(tempDir);
            
            Path tempLib = tempDir.resolve(libName);
            
            // Extract if not exists or if newer in JAR
            if (!Files.exists(tempLib)) {
                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                tempLib.toFile().setExecutable(true);
            }
            
            return tempLib;
        } catch (IOException e) {
            System.err.println("Failed to extract library from JAR: " + e.getMessage());
            return null;
        }
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
    
    /**
     * Calculates squared Euclidean distance between two color points.
     * 
     * @param arena memory arena for native allocation
     * @param point1 first color point as [c1, c2, c3]
     * @param point2 second color point as [c1, c2, c3]
     * @return squared distance: (c1a-c1b)² + (c2a-c2b)² + (c3a-c3b)²
     */
    public float distanceSquared(Arena arena, float[] point1, float[] point2) {
        if (distance_squared == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        MemorySegment p1 = arena.allocate(COLOR_POINT_LAYOUT);
        MemorySegment p2 = arena.allocate(COLOR_POINT_LAYOUT);
        
        p1.set(ValueLayout.JAVA_FLOAT, 0, point1[0]);
        p1.set(ValueLayout.JAVA_FLOAT, 4, point1[1]);
        p1.set(ValueLayout.JAVA_FLOAT, 8, point1[2]);
        
        p2.set(ValueLayout.JAVA_FLOAT, 0, point2[0]);
        p2.set(ValueLayout.JAVA_FLOAT, 4, point2[1]);
        p2.set(ValueLayout.JAVA_FLOAT, 8, point2[2]);
        
        try {
            return (float) distance_squared.invokeExact(p1, p2);
        } catch (Throwable t) {
            throw new RuntimeException("distance_squared native call failed", t);
        }
    }
    
    /**
     * Checks if distance_squared function is available.
     */
    public boolean hasDistanceSquared() {
        return distance_squared != null;
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
            @SuppressWarnings("unused")
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
    
    public int[] posterizeImage(Arena arena, int[] imagePixels, int width, int height,
                                 float[] targetPalette, float[] sourcePalette) {
        if (posterize_image == null) {
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
            posterize_image.invokeExact(
                imageNative, width, height,
                targetPaletteNative, sourcePaletteNative, paletteSize, outputNative
            );
            
            int[] result = new int[n];
            MemorySegment.ofArray(result).copyFrom(outputNative);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Posterize native call failed", t);
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
            int changed = (int) assign_points_batch.invokeExact(pointsNative, n, centroidsNative, k, assignmentsNative);
            
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
            @SuppressWarnings("unused")
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
    
    public float[] samplePixelsFromImage(Arena arena, int[] imagePixels, int sampleSize, long seed) {
        if (sample_pixels_from_image == null) {
            throw new UnsupportedOperationException("Native library not loaded");
        }
        
        int totalPixels = imagePixels.length;
        int maxSamples = Math.min(totalPixels, sampleSize);
        
        MemorySegment imageNative = arena.allocate(ValueLayout.JAVA_INT, totalPixels);
        MemorySegment outputNative = arena.allocate(COLOR_POINT_LAYOUT, maxSamples);
        
        imageNative.copyFrom(MemorySegment.ofArray(imagePixels));
        
        try {
            int actualSize = (int) sample_pixels_from_image.invokeExact(
                imageNative, totalPixels, outputNative, sampleSize, seed
            );
            
            float[] result = new float[actualSize * 3];
            for (int i = 0; i < actualSize; i++) {
                long offset = i * COLOR_POINT_LAYOUT.byteSize();
                result[i * 3] = outputNative.get(ValueLayout.JAVA_FLOAT, offset);
                result[i * 3 + 1] = outputNative.get(ValueLayout.JAVA_FLOAT, offset + 4);
                result[i * 3 + 2] = outputNative.get(ValueLayout.JAVA_FLOAT, offset + 8);
            }
            
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Sample pixels from image native call failed", t);
        }
    }
    
    public record DecodedImage(int width, int height, int[] pixels) {}
    
    public DecodedImage decodeJpegFile(String filePath) {
        if (decode_jpeg_file_turbojpeg == null || turbojpeg_free == null) {
            return null; // TurboJPEG not available
        }
        
        MemorySegment nativePixels = null;
        
        try (Arena arena = Arena.ofConfined()) {
            // Allocate path string
            MemorySegment pathNative = arena.allocateFrom(filePath);
            
            // Allocate output pointers
            MemorySegment widthPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment heightPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment pixelsPtr = arena.allocate(ValueLayout.ADDRESS);
            
            int result = (int) decode_jpeg_file_turbojpeg.invokeExact(
                pathNative, widthPtr, heightPtr, pixelsPtr
            );
            
            if (result != 0) {
                return null; // Decoding failed
            }
            
            int width = widthPtr.get(ValueLayout.JAVA_INT, 0);
            int height = heightPtr.get(ValueLayout.JAVA_INT, 0);
            nativePixels = pixelsPtr.get(ValueLayout.ADDRESS, 0);
            
            if (nativePixels.equals(MemorySegment.NULL)) {
                return null;
            }
            
            // Reinterpret the segment to proper size and copy data
            int numPixels = width * height;
            MemorySegment pixels = nativePixels.reinterpret(numPixels * 4L);
            
            int[] pixelArray = new int[numPixels];
            MemorySegment.ofArray(pixelArray).copyFrom(pixels);
            
            // Free native memory
            turbojpeg_free.invokeExact(nativePixels);
            nativePixels = null;
            
            return new DecodedImage(width, height, pixelArray);
        } catch (Throwable t) {
            // Try to free memory if allocated
            if (nativePixels != null && !nativePixels.equals(MemorySegment.NULL)) {
                try {
                    turbojpeg_free.invokeExact(nativePixels);
                } catch (Throwable ignored) {}
            }
            System.err.println("TurboJPEG decode failed: " + t.getMessage());
            return null;
        }
    }
    
    /**
     * Decode JPEG from byte array (works with Unicode paths by reading in Java).
     */
    public DecodedImage decodeJpegBuffer(byte[] jpegData) {
        if (turbojpeg_decode_buffer == null || turbojpeg_free == null) {
            return null;
        }
        
        MemorySegment nativePixels = null;
        
        try (Arena arena = Arena.ofConfined()) {
            // Copy JPEG data to native memory
            MemorySegment jpegNative = arena.allocate(jpegData.length);
            jpegNative.copyFrom(MemorySegment.ofArray(jpegData));
            
            // Allocate output pointers
            MemorySegment widthPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment heightPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment pixelsPtr = arena.allocate(ValueLayout.ADDRESS);
            
            int result = (int) turbojpeg_decode_buffer.invokeExact(
                jpegNative, (long) jpegData.length, widthPtr, heightPtr, pixelsPtr
            );
            
            if (result != 0) {
                return null;
            }
            
            int width = widthPtr.get(ValueLayout.JAVA_INT, 0);
            int height = heightPtr.get(ValueLayout.JAVA_INT, 0);
            nativePixels = pixelsPtr.get(ValueLayout.ADDRESS, 0);
            
            if (nativePixels.equals(MemorySegment.NULL)) {
                return null;
            }
            
            int numPixels = width * height;
            MemorySegment pixels = nativePixels.reinterpret(numPixels * 4L);
            
            int[] pixelArray = new int[numPixels];
            MemorySegment.ofArray(pixelArray).copyFrom(pixels);
            
            turbojpeg_free.invokeExact(nativePixels);
            nativePixels = null;
            
            return new DecodedImage(width, height, pixelArray);
        } catch (Throwable t) {
            if (nativePixels != null && !nativePixels.equals(MemorySegment.NULL)) {
                try {
                    turbojpeg_free.invokeExact(nativePixels);
                } catch (Throwable ignored) {}
            }
            System.err.println("TurboJPEG buffer decode failed: " + t.getMessage());
            return null;
        }
    }
    
    public boolean hasTurboJpeg() {
        if (aichat_has_turbojpeg == null) {
            return false;
        }
        try {
            int result = (int) aichat_has_turbojpeg.invokeExact();
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Encode and save image as JPEG using TurboJPEG.
     * @param pixels ARGB pixel array
     * @param width image width
     * @param height image height
     * @param quality JPEG quality (1-100, recommended 85-95)
     * @param filePath output file path
     * @return true if successful
     */
    public boolean encodeJpegToFile(int[] pixels, int width, int height, int quality, String filePath) {
        if (turbojpeg_encode_to_file == null) {
            return false;
        }
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixelsNative = arena.allocate(ValueLayout.JAVA_INT, pixels.length);
            pixelsNative.copyFrom(MemorySegment.ofArray(pixels));
            
            MemorySegment pathNative = arena.allocateFrom(filePath);
            
            int result = (int) turbojpeg_encode_to_file.invokeExact(
                pixelsNative, width, height, quality, pathNative
            );
            
            return result == 0;
        } catch (Throwable t) {
            System.err.println("TurboJPEG encode failed: " + t.getMessage());
            return false;
        }
    }
    
    // ==================== OpenCL GPU Acceleration ====================
    
    /**
     * Check if OpenCL is available on this system.
     */
    public boolean hasOpenCL() {
        if (aichat_has_opencl == null) return false;
        try {
            return ((int) aichat_has_opencl.invokeExact()) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Initialize OpenCL context. Called automatically on first use.
     */
    public boolean initOpenCL() {
        if (opencl_init == null) return false;
        try {
            return ((int) opencl_init.invokeExact()) == 0;
        } catch (Throwable t) {
            System.err.println("OpenCL init failed: " + t.getMessage());
            return false;
        }
    }
    
    /**
     * Cleanup OpenCL resources.
     */
    public void cleanupOpenCL() {
        if (opencl_cleanup == null) return;
        try {
            opencl_cleanup.invokeExact();
        } catch (Throwable ignored) {}
    }
    
    /**
     * Get OpenCL device name.
     */
    public String getOpenCLDeviceName() {
        if (opencl_get_device_name == null) return "N/A";
        try {
            MemorySegment ptr = (MemorySegment) opencl_get_device_name.invokeExact();
            return ptr.reinterpret(256).getString(0);
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }
    
    /**
     * GPU-accelerated image resynthesis using OpenCL.
     * @return result pixels, or null if failed
     */
    public int[] resynthesizeImageGPU(Arena arena, int[] imagePixels, int width, int height,
                                       float[] targetPalette, float[] sourcePalette) {
        if (opencl_resynthesize_image == null) {
            return null;
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
            int result = (int) opencl_resynthesize_image.invokeExact(
                imageNative, width, height,
                targetPaletteNative, sourcePaletteNative, paletteSize, outputNative
            );
            
            if (result != 0) {
                return null;
            }
            
            int[] output = new int[n];
            MemorySegment.ofArray(output).copyFrom(outputNative);
            return output;
        } catch (Throwable t) {
            System.err.println("OpenCL resynthesis failed: " + t.getMessage());
            return null;
        }
    }
    
    /**
     * GPU-accelerated streaming resynthesis for very large images.
     * Uses double-buffering to overlap GPU compute with CPU I/O.
     * @param tileHeight Height of each processing tile (0 = auto)
     */
    public int[] resynthesizeImageGPUStreaming(Arena arena, int[] imagePixels, int width, int height,
                                                float[] targetPalette, float[] sourcePalette, 
                                                int tileHeight) {
        if (opencl_resynthesize_streaming == null) {
            return null;
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
            int result = (int) opencl_resynthesize_streaming.invokeExact(
                imageNative, width, height,
                targetPaletteNative, sourcePaletteNative, paletteSize, outputNative,
                tileHeight
            );
            
            if (result != 0) {
                return null;
            }
            
            int[] output = new int[n];
            MemorySegment.ofArray(output).copyFrom(outputNative);
            return output;
        } catch (Throwable t) {
            System.err.println("OpenCL streaming resynthesis failed: " + t.getMessage());
            return null;
        }
    }
}
