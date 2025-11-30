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
