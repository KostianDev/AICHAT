# AICHAT Native Library Makefile
# Builds optimized shared library for use with Java Panama FFI

# Detect OS
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
    TARGET = libaichat_native.so
    LDFLAGS = -shared -fPIC
    PLATFORM = linux
endif
ifeq ($(UNAME_S),Darwin)
    TARGET = libaichat_native.dylib
    LDFLAGS = -dynamiclib
    PLATFORM = macos
endif

# Compiler settings
CC = gcc
CFLAGS = -O3 -march=native -ffast-math -fPIC -Wall -Wextra
CFLAGS += -DNDEBUG

# Enable OpenMP for parallelization if available
CFLAGS += -fopenmp
LDFLAGS += -fopenmp

# SIMD flags (auto-detected with -march=native, but explicit for clarity)
# For SSE: -msse -msse2 -msse3 -msse4.1 -msse4.2
# For AVX: -mavx -mavx2
# Using -march=native auto-detects best options

# Source files
SRC_DIR = src
BUILD_DIR = build
SRCS = $(SRC_DIR)/aichat_native.c $(SRC_DIR)/dbscan_optimized.c
OBJS = $(BUILD_DIR)/aichat_native.o $(BUILD_DIR)/dbscan_optimized.o

# Output directories
LIB_DIR = ../app/src/main/resources/native/$(PLATFORM)

# Targets
.PHONY: all clean install debug

all: $(BUILD_DIR) $(LIB_DIR) $(TARGET)
	@echo "Build complete: $(TARGET)"
	@echo "Library installed to: $(LIB_DIR)/$(TARGET)"

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(LIB_DIR):
	mkdir -p $(LIB_DIR)

$(TARGET): $(OBJS)
	$(CC) $(LDFLAGS) -o $(BUILD_DIR)/$(TARGET) $(OBJS) -lm
	cp $(BUILD_DIR)/$(TARGET) $(LIB_DIR)/

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c $(SRC_DIR)/aichat_native.h
	$(CC) $(CFLAGS) -c $< -o $@

debug: CFLAGS = -O0 -g -fPIC -Wall -Wextra -fsanitize=address
debug: LDFLAGS += -fsanitize=address
debug: all

clean:
	rm -rf $(BUILD_DIR)
	rm -f $(LIB_DIR)/$(TARGET)

# Test compilation
test: $(TARGET)
	@echo "Testing library loading..."
	@if [ -f $(LIB_DIR)/$(TARGET) ]; then \
		echo "Library exists: OK"; \
		file $(LIB_DIR)/$(TARGET); \
	else \
		echo "Library not found: FAIL"; \
		exit 1; \
	fi

# Print info about the build
info:
	@echo "Platform: $(PLATFORM)"
	@echo "Target: $(TARGET)"
	@echo "CC: $(CC)"
	@echo "CFLAGS: $(CFLAGS)"
	@echo "LDFLAGS: $(LDFLAGS)"
	@echo "Output: $(LIB_DIR)/$(TARGET)"
