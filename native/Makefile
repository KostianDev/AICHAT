# AICHAT Native Library - Makefile

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
    LIB_TARGET = libaichat_native.so
    LDFLAGS = -shared -fPIC -fopenmp
    PLATFORM = linux
    # Check for OpenCL
    OPENCL_CHECK := $(shell pkg-config --exists OpenCL 2>/dev/null && echo yes)
    ifeq ($(OPENCL_CHECK),yes)
        OPENCL_CFLAGS := $(shell pkg-config --cflags OpenCL)
        OPENCL_LIBS := $(shell pkg-config --libs OpenCL)
        HAS_OPENCL = 1
    else
        # Try direct linking (common on systems with GPU drivers)
        OPENCL_TEST := $(shell echo 'int main(){}' | gcc -x c - -lOpenCL -o /dev/null 2>/dev/null && echo yes)
        ifeq ($(OPENCL_TEST),yes)
            OPENCL_LIBS = -lOpenCL
            HAS_OPENCL = 1
        endif
    endif
endif
ifeq ($(UNAME_S),Darwin)
    LIB_TARGET = libaichat_native.dylib
    LDFLAGS = -dynamiclib -fopenmp
    PLATFORM = macos
    OPENCL_LIBS = -framework OpenCL
    HAS_OPENCL = 1
endif

CC = gcc
CFLAGS = -O3 -march=native -mavx2 -ffast-math -fPIC -Wall -Wextra -DNDEBUG -fopenmp $(OPENCL_CFLAGS)
LIBS = -lm -lturbojpeg $(OPENCL_LIBS)

SRC_DIR = src
INC_DIR = include
BUILD_DIR = build
TARGET_DIR = ../app/src/main/resources/native/$(PLATFORM)

SRCS = $(SRC_DIR)/common.c $(SRC_DIR)/distance.c $(SRC_DIR)/kmeans.c $(SRC_DIR)/hybrid.c $(SRC_DIR)/color.c $(SRC_DIR)/image.c $(SRC_DIR)/turbojpeg_wrapper.c

# Add OpenCL source if available
ifdef HAS_OPENCL
    SRCS += $(SRC_DIR)/opencl_accel.c
    CFLAGS += -DHAVE_OPENCL
endif

OBJS = $(patsubst $(SRC_DIR)/%.c,$(BUILD_DIR)/%.o,$(SRCS))
TARGET = $(BUILD_DIR)/$(LIB_TARGET)

.PHONY: all clean install info

all: info $(BUILD_DIR) $(TARGET) install

info:
ifdef HAS_OPENCL
	@echo "OpenCL support: ENABLED"
else
	@echo "OpenCL support: DISABLED (install OpenCL development files for GPU acceleration)"
endif

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c
	$(CC) $(CFLAGS) -I$(INC_DIR) -c $< -o $@

$(TARGET): $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $^ $(LIBS)

install: $(TARGET)
	mkdir -p $(TARGET_DIR)
	cp $(TARGET) $(TARGET_DIR)/
	@echo "Build complete: $(LIB_TARGET)"
	@echo "Installed to: $(TARGET_DIR)/$(LIB_TARGET)"

clean:
	rm -rf $(BUILD_DIR)
	rm -f $(TARGET_DIR)/$(LIB_TARGET)
