# AICHAT - Distribution

## Distribution Types

### Full Distribution (Recommended)
**No Java installation required.** Includes bundled JRE with JavaFX.

- `aichat-full-X.X.X-linux.tar.gz` / `.zip` (~45 MB)
- `aichat-full-X.X.X-macos.tar.gz` / `.zip`
- `aichat-full-X.X.X-windows.zip`

### Portable Distribution
Requires Java 25+ installed on system.

- `aichat-portable-X.X.X.tar.gz` / `.zip` (~11 MB)

## Running

### Linux/macOS
```bash
./aichat.sh
```

### Windows
```cmd
aichat.bat
```

## GPU Acceleration (Optional)

OpenCL enables GPU acceleration for large images (~85x faster).

### Linux
AMD GPU (Mesa rusticl):
```bash
sudo apt install mesa-opencl-icd    # Ubuntu/Debian
sudo dnf install mesa-libOpenCL     # Fedora
sudo pacman -S opencl-rusticl-mesa  # Arch
```

NVIDIA GPU:
```bash
sudo apt install nvidia-driver-xxx nvidia-opencl-icd
```

Intel GPU:
```bash
sudo apt install intel-opencl-icd
```

### Windows/macOS
OpenCL is included with GPU drivers.

## Native Library Dependencies

For native acceleration (recommended):
```bash
sudo apt install libturbojpeg0      # Ubuntu/Debian
sudo dnf install turbojpeg          # Fedora
brew install jpeg-turbo             # macOS
```

## Troubleshooting

**GPU not detected**: Run `clinfo` to verify OpenCL installation.

**Native library errors**: Application falls back to Java implementation automatically.
