# This is the Android makefile for libyuv for both platform and NDK.
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := librtmp
LOCAL_SRC_FILES := \
    librtmp/amf.c           \
    librtmp/hashswf.c    \
    librtmp/log.c           \
    librtmp/parseurl.c      \
    librtmp/rtmp.c	\
    RtmpHandle.cpp	\
    RtmpUtil.cpp

LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)


