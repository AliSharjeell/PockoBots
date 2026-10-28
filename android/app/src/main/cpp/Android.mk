LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := pocket-host-compat
LOCAL_SRC_FILES := host-compat.c
LOCAL_LDLIBS := -ldl
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDFLAGS += -Wl,--version-script=$(LOCAL_PATH)/host-compat.map
include $(BUILD_SHARED_LIBRARY)
