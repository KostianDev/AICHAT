# AICHAT - Advanced Image Color Harmony Analysis and Transformation

A high-performance image processing application for extracting color palettes and transforming images based on color harmony principles. Built with Java 25, using native acceleration through Panama FFM API.

## Overview

AICHAT analyzes images using sophisticated clustering algorithms to extract meaningful color palettes in perceptually uniform color spaces. The extracted palettes can then be applied to transform other images while preserving their structure and luminance relationships.

The application supports both RGB and CIELAB color models, with CIELAB providing perceptually accurate color differences using the CIEDE2000 (ΔE00) metric - the industry standard for measuring color similarity as perceived by the human eye.

## Features

**Color Palette Extraction**
- Hybrid K-Means++ clustering algorithm optimized for color data
- Support for palettes from 2 to 512 colors
- Reservoir sampling for processing large images efficiently
- Deterministic results with configurable random seed

**Color Models**
- RGB color space for fast processing
- CIELAB color space for perceptually uniform clustering
- CIEDE2000 color difference metric with reference validation

**Image Resynthesis**
- Transfer color palettes between images
- Hungarian algorithm for optimal color mapping
- Preserve image structure while transforming colors

**Performance Optimizations**
- Native C library with SIMD acceleration (AVX2/SSE4)
- OpenCL GPU acceleration for resynthesis
- LUT-based color mapping for palettes ≤256 colors
- TurboJPEG integration for fast JPEG decoding
- Tiled processing for images larger than 16MP

**Export Formats**
- GIMP Palette (.gpl)
- CSV with LAB values
- PNG palette visualization

## System Requirements

- Java 25 or later (with JavaFX)
- Linux x64 or Windows x64
- Optional: OpenCL-capable GPU for acceleration

## Building from Source

```bash
# Build and run
./gradlew run

# Run tests
./gradlew test

# Run full test suite (unit tests + JMH benchmarks + Pitest mutation testing)
./gradlew allTests

# Run with GPU acceleration (AMD/Intel Mesa)
RUSTICL_ENABLE=radeonsi ./gradlew allTests  # AMD
RUSTICL_ENABLE=iris ./gradlew allTests      # Intel

# Create distribution packages
./gradlew allDist
```

## Distribution Packages

| Package | Size | Description |
|---------|------|-------------|
| `aichat-portable-*-linux.tar.gz` | ~5 MB | Requires Java 25+ installed |
| `aichat-full-*-linux.zip` | ~45 MB | Bundled JRE, no dependencies |
| `aichat-portable-*-windows.zip` | ~5 MB | Requires Java 25+ installed |
| `aichat-full-*-windows.zip` | ~330 MB | Bundled Liberica JDK with JavaFX |

## Testing Strategy

The project uses a comprehensive multi-layered testing approach:

- **Unit Tests** - JUnit 5 tests for all core components
- **Property-Based Tests** - jqwik for mathematical invariants (round-trip conversions, symmetry)
- **Reference Tests** - Validation against published scientific data
- **Visual Regression** - Golden master comparison using image-comparison library
- **Load Tests** - Performance validation on images up to 16MP with high color counts
- **Mutation Testing** - Pitest for test suite quality assessment (77% mutation score)
- **Benchmarks** - JMH micro-benchmarks for performance regression detection
- **Differential Tests** - Comparison of Java and Native implementations for correctness
- **Fine-grained Math Tests** - Exact value verification for distance calculations

### Test Profiles

The project supports multiple test profiles to validate different execution paths:

```bash
# Run tests with Java-only implementation (disable native)
./gradlew testJava

# Run tests with native acceleration (default)
./gradlew testNative

# Run both profiles sequentially
./gradlew testAllProfiles

# Manual profile selection
./gradlew test -Dforce.java=true
```

### Performance Test Output

Load tests export machine-readable results in CSV and JSON formats:
- `test-results/performance/performance-results.csv` - For spreadsheet analysis
- `test-results/performance/performance-results.json` - For automated processing

All test reports are saved to `test-results/` directory (tracked in git) for grading:
- `test-results/reports/test/` - JUnit HTML reports (native mode)
- `test-results/reports/testJava/` - JUnit HTML reports (Java fallback)
- `test-results/pitest/` - Mutation testing coverage
- `test-results/jmh/` - JMH benchmark results

## Multi-Path Architecture

AICHAT implements a **dual-path architecture** with automatic fallback:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application Layer                        │
├─────────────────────────────────────────────────────────────────┤
│                     ImageHarmonyEngine                          │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐          │
│  │   analyze   │ -> │  cluster    │ -> │ resynthesize│          │
│  └─────────────┘    └─────────────┘    └─────────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                      Execution Paths                            │
│                                                                 │
│  ┌─────────────────────┐     ┌─────────────────────────────┐    │
│  │   Native (C/SIMD)   │ OR  │    Java Fallback            │    │
│  │  - AVX2/SSE4        │     │  - Pure Java implementation │    │
│  │  - OpenMP parallel  │     │  - Always available         │    │
│  │  - OpenCL GPU       │     │  - Reference implementation │    │
│  └─────────────────────┘     └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Native Optimizations

The native library (`libaichat_native.so` / `aichat_native.dll`) uses **all available optimizations simultaneously** (not mutually exclusive):

| Optimization | Description | Status |
|--------------|-------------|--------|
| **SIMD (AVX2)** | Vectorized operations via compiler auto-vectorization | Always enabled (`-mavx2 -O3`) |
| **OpenMP** | Multi-threaded parallel loops for batch operations | Always enabled (`-fopenmp`) |
| **OpenCL** | GPU acceleration for resynthesis | Auto-detected at build time |
| **TurboJPEG** | Fast JPEG decoding | Auto-detected at build time |

**Note:** This native code uses all optimizations together. The compiler automatically vectorizes loops with AVX2, and OpenMP parallelizes batch operations.

### Path Selection

The execution path is selected automatically:

1. **Native path** (default): Used when `libaichat_native.so` loads successfully
2. **Java fallback**: Used when native library is unavailable or disabled

To force Java-only mode:
```bash
java -Dforce.java=true -jar app.jar
```

### Important Notes for Contributors

1. **Both paths must produce equivalent results** - The differential tests verify this
2. **Java is the reference implementation** - Native must match Java behavior
3. **All changes must pass both test profiles** (`testJava` + `testNative`)
4. **Fine-grained math tests** catch low-level bugs (e.g., incorrect distance formula)

## Performance

Measured on AMD Ryzen 5 5600X, 32GB RAM, Mesa OpenCL:

| Image Size | Palette Size | Analysis | Resynthesis |
|------------|--------------|----------|-------------|
| 2 MP (1080p) | 64 colors | ~200 ms | ~50 ms |
| 8 MP (4K) | 256 colors | ~2 s | ~200 ms |
| 16 MP | 256 colors | ~5 s | ~500 ms |

Resynthesis with native acceleration achieves 30-100+ MP/s throughput depending on hardware.

## License

This project was developed as a coursework for "Software Engineering Components: Quality and Testing" at Igor Sikorsky Kyiv Polytechnic Institute.

## Author

Kostiantyn Verbytskyi, IM-31, FIOT, KPI
