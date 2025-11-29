# AICHAT Native Library - Makefile

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
    LIB_TARGET = libaichat_native.so
    LDFLAGS = -shared -fPIC -fopenmp
    PLATFORM = linux
endif
ifeq ($(UNAME_S),Darwin)
    LIB_TARGET = libaichat_native.dylib
    LDFLAGS = -dynamiclib -fopenmp
    PLATFORM = macos
endif

CC = gcc
CFLAGS = -O3 -march=native -ffast-math -fPIC -Wall -Wextra -DNDEBUG -fopenmp
LIBS = -lm

SRC_DIR = src
INC_DIR = include
BUILD_DIR = build
TARGET_DIR = ../app/src/main/resources/native/$(PLATFORM)

SRCS = $(SRC_DIR)/common.c $(SRC_DIR)/distance.c $(SRC_DIR)/kmeans.c $(SRC_DIR)/dbscan.c $(SRC_DIR)/color.c $(SRC_DIR)/image.c
OBJS = $(patsubst $(SRC_DIR)/%.c,$(BUILD_DIR)/%.o,$(SRCS))
TARGET = $(BUILD_DIR)/$(LIB_TARGET)

.PHONY: all clean install

all: $(BUILD_DIR) $(TARGET) install

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
