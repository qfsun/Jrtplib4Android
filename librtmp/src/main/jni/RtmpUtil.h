#include <jni.h>
#include <android/log.h>
#include <exception>
#include <string.h>
#include "pthread.h"
#include "librtmp/rtmp.h"
#include "librtmp/amf.h"
#include "librtmp/rtmp_sys.h"
#include "librtmp/log.h"
#ifndef LIBRTMP_RTMPUTIL_H
#define LIBRTMP_RTMPUTIL_H
#ifdef __cplusplus
extern "C" {
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>

#define HTON16(x)  ((x>>8&0xff)|(x<<8&0xff00))
#define HTON24(x)  ((x>>16&0xff)|(x<<16&0xff0000)|(x&0xff00))
#define HTON32(x)  ((x>>24&0xff)|(x>>8&0xff00)|\
    (x<<8&0xff0000)|(x<<24&0xff000000))
#define HTONTIME(x) ((x>>16&0xff)|(x<<16&0xff0000)|(x&0xff00)|(x&0xff000000))

using namespace std;

//定义日志宏变量
#define logw(content)   __android_log_write(ANDROID_LOG_WARN,"WTOE JNI Warning",content)
#define loge(content)   __android_log_write(ANDROID_LOG_ERROR,"WTOE JNI Error",content)
#define logd(content)   __android_log_write(ANDROID_LOG_DEBUG,"WTOE JNI Debug",content)

#endif

class RtmpUtils {

public:
    int initRtmpHandle(const char *url_);
    int PushH264Data(const unsigned char* data, int dataLen, bool isIdr);
    int PushSPSPPS(const unsigned char* sps, int spsLen, const unsigned char* pps, int ppsLen);
    int pushFlvData(unsigned char *buf_, jint length);
    int pushFlvFile(const char *url, const char *path);
    void releaseRtmpHandle();

private:
    bool is_init;//初始化标识
    RTMP *rtmp = NULL;
    RTMPPacket *packet = NULL;
    int mCount = 0;
    long mTimestamp;
};
#ifdef __cplusplus
}
#endif
#endif //LIBRTMP_RTMPUTIL_H
