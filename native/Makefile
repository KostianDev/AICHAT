# AICHAT Native Library - Makefile
# Supports: Linux (native), macOS (native), Windows (cross-compile with MinGW)

UNAME_S := $(shell uname -s)
NATIVE_DIR := $(dir $(lastword $(MAKEFILE_LIST)))

# Windows cross-compilation
ifdef CROSS_WINDOWS
    LIB_TARGET = aichat_native.dll
    CC = x86_64-w64-mingw32-gcc
    LDFLAGS = -shared -static-libgcc -static
    PLATFORM = windows
    CFLAGS_PLATFORM = -O3 -mavx2 -ffast-math -Wall -Wextra -DNDEBUG
    
    DEPS_DIR = $(NATIVE_DIR)deps/windows
    TURBOJPEG_DIR = $(DEPS_DIR)/libjpeg-turbo
    OPENCL_DIR = $(DEPS_DIR)/opencl
    
    # TurboJPEG
    ifneq ($(wildcard $(TURBOJPEG_DIR)/include/turbojpeg.h),)
        CFLAGS_PLATFORM += -I$(TURBOJPEG_DIR)/include -DHAVE_TURBOJPEG
        LIBS = -L$(TURBOJPEG_DIR)/lib -l:libturbojpeg.a
        HAS_TURBOJPEG = 1
    else
        LIBS = -lm
    endif
    
    # OpenCL
    ifneq ($(wildcard $(OPENCL_DIR)/include/CL/cl.h),)
        CFLAGS_PLATFORM += -I$(OPENCL_DIR)/include -DHAVE_OPENCL
        LIBS += -L$(OPENCL_DIR)/lib -lOpenCL
        HAS_OPENCL = 1
    endif

else ifeq ($(UNAME_S),Linux)
    LIB_TARGET = libaichat_native.so
    CC = gcc
    LDFLAGS = -shared -fPIC -fopenmp
    PLATFORM = linux
    CFLAGS_PLATFORM = -O3 -march=native -mavx2 -ffast-math -fPIC -Wall -Wextra -DNDEBUG -fopenmp -DHAVE_TURBOJPEG
    LIBS = -lm -lturbojpeg
    HAS_TURBOJPEG = 1
    
    OPENCL_CHECK := $(shell pkg-config --exists OpenCL 2>/dev/null && echo yes)
    ifeq ($(OPENCL_CHECK),yes)
        OPENCL_CFLAGS := $(shell pkg-config --cflags OpenCL)
        OPENCL_LIBS := $(shell pkg-config --libs OpenCL)
        HAS_OPENCL = 1
    else
        OPENCL_TEST := $(shell echo 'int main(){}' | gcc -x c - -lOpenCL -o /dev/null 2>/dev/null && echo yes)
        ifeq ($(OPENCL_TEST),yes)
            OPENCL_LIBS = -lOpenCL
            HAS_OPENCL = 1
        endif
    endif
    LIBS += $(OPENCL_LIBS)

else ifeq ($(UNAME_S),Darwin)
    LIB_TARGET = libaichat_native.dylib
    CC = gcc
    LDFLAGS = -dynamiclib -fopenmp
    PLATFORM = macos
    CFLAGS_PLATFORM = -O3 -march=native -mavx2 -ffast-math -fPIC -Wall -Wextra -DNDEBUG -fopenmp -DHAVE_TURBOJPEG
    OPENCL_LIBS = -framework OpenCL
    HAS_OPENCL = 1
    HAS_TURBOJPEG = 1
    LIBS = -lm -lturbojpeg $(OPENCL_LIBS)
endif

CFLAGS = $(CFLAGS_PLATFORM) $(OPENCL_CFLAGS)
ifdef HAS_OPENCL
    CFLAGS += -DHAVE_OPENCL
endif

SRC_DIR = src
INC_DIR = include
BUILD_DIR = build
TARGET_DIR = ../app/src/main/resources/native/$(PLATFORM)

SRCS = $(SRC_DIR)/common.c $(SRC_DIR)/distance.c $(SRC_DIR)/kmeans.c $(SRC_DIR)/hybrid.c $(SRC_DIR)/color.c $(SRC_DIR)/image.c

ifdef HAS_TURBOJPEG
    SRCS += $(SRC_DIR)/turbojpeg_wrapper.c
endif
ifdef HAS_OPENCL
    SRCS += $(SRC_DIR)/opencl_accel.c
endif

OBJS = $(patsubst $(SRC_DIR)/%.c,$(BUILD_DIR)/%.o,$(SRCS))
TARGET = $(BUILD_DIR)/$(LIB_TARGET)

.PHONY: all clean install info windows linux macos all-platforms

all: info $(BUILD_DIR) $(TARGET) install

info:
	@echo "=== AICHAT Native Build ==="
	@echo "Platform: $(PLATFORM)"
	@echo "TurboJPEG: $(if $(HAS_TURBOJPEG),YES,NO)"
	@echo "OpenCL: $(if $(HAS_OPENCL),YES,NO)"

linux:
	$(MAKE) clean
	$(MAKE)

windows:
	$(MAKE) clean
	$(MAKE) CROSS_WINDOWS=1

all-platforms:
	@echo "=== Building for Linux ==="
	$(MAKE) linux
	@echo ""
	@echo "=== Building for Windows ==="
	$(MAKE) windows

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c
	$(CC) $(CFLAGS) -I$(INC_DIR) -c $< -o $@

$(TARGET): $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $^ $(LIBS)

install: $(TARGET)
	mkdir -p $(TARGET_DIR)
	cp $(TARGET) $(TARGET_DIR)/
ifdef CROSS_WINDOWS
	@# Copy DLL dependencies for Windows
	@if [ -f "$(TURBOJPEG_DIR)/bin/libturbojpeg.dll" ]; then \
		cp $(TURBOJPEG_DIR)/bin/libturbojpeg.dll $(TARGET_DIR)/; \
	fi
endif
	@echo "Installed: $(TARGET_DIR)/$(LIB_TARGET)"

clean:
	rm -rf $(BUILD_DIR)
