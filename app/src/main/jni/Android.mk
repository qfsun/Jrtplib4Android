LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE  := jthread
LOCAL_SRC_FILES := libjthread.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE  := jrtp
LOCAL_SRC_FILES := libjrtplib.a
LOCAL_STATIC_LIBRARIES := jthread
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := rtp-handle
LOCAL_SRC_FILES := RtpHandle.cpp RtpReceiver.cpp RtpSender.cpp RtpCommon.cpp
LOCAL_STATIC_LIBRARIES := jthread jrtp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)