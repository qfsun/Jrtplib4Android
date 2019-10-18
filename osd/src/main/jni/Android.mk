LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := yuv-osd
LOCAL_SRC_FILES := YuvOsdUtils.cpp YuvUtils.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)