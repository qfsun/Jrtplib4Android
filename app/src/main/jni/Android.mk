LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := rtp-handle
LOCAL_SRC_FILES := \
		jthread/jmutex.cpp	\
        jthread/jthread.cpp	\
        JRTPLIB/src/rtcpapppacket.cpp	\
        JRTPLIB/src/rtcpbyepacket.cpp	\
        JRTPLIB/src/rtcpcompoundpacket.cpp	\
        JRTPLIB/src/rtcpcompoundpacketbuilder.cpp	\
        JRTPLIB/src/rtcppacket.cpp	\
        JRTPLIB/src/rtcppacketbuilder.cpp	\
        JRTPLIB/src/rtcprrpacket.cpp	\
        JRTPLIB/src/rtcpscheduler.cpp	\
        JRTPLIB/src/rtcpsdesinfo.cpp	\
        JRTPLIB/src/rtcpsdespacket.cpp	\
        JRTPLIB/src/rtcpsrpacket.cpp	\
        JRTPLIB/src/rtpcollisionlist.cpp	\
        JRTPLIB/src/rtpdebug.cpp	\
        JRTPLIB/src/rtperrors.cpp	\
        JRTPLIB/src/rtpinternalsourcedata.cpp	\
        JRTPLIB/src/rtpipv4address.cpp	\
        JRTPLIB/src/rtpipv6address.cpp	\
        JRTPLIB/src/rtpipv4destination.cpp	\
        JRTPLIB/src/rtpipv6destination.cpp	\
        JRTPLIB/src/rtplibraryversion.cpp	\
        JRTPLIB/src/rtppacket.cpp	\
        JRTPLIB/src/rtppacketbuilder.cpp	\
        JRTPLIB/src/rtppollthread.cpp	\
        JRTPLIB/src/rtprandom.cpp	\
        JRTPLIB/src/rtprandomrand48.cpp	\
        JRTPLIB/src/rtprandomrands.cpp	\
        JRTPLIB/src/rtprandomurandom.cpp	\
        JRTPLIB/src/rtpsession.cpp	\
        JRTPLIB/src/rtpsessionparams.cpp	\
        JRTPLIB/src/rtpsessionsources.cpp	\
        JRTPLIB/src/rtpsourcedata.cpp	\
        JRTPLIB/src/rtpsources.cpp	\
        JRTPLIB/src/rtptimeutilities.cpp	\
        JRTPLIB/src/rtpudpv4transmitter.cpp	\
        JRTPLIB/src/rtpudpv6transmitter.cpp	\
        JRTPLIB/src/rtpbyteaddress.cpp	\
        JRTPLIB/src/rtpexternaltransmitter.cpp	\
        JRTPLIB/src/rtpsecuresession.cpp	\
        JRTPLIB/src/rtpabortdescriptors.cpp	\
        JRTPLIB/src/rtptcpaddress.cpp	\
        JRTPLIB/src/rtptcptransmitter.cpp	\
        RtpHandle.cpp	\
        RtpReceiver.cpp	\
        RtpSender.cpp	\
        RtpCommon.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)