#include <jni.h>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <sys/time.h>

#ifndef JRTPLIB_YUVUTILS_H
#define JRTPLIB_YUVUTILS_H
#ifdef __cplusplus
extern "C" {
#endif

class YuvUtils {

public:
    YuvUtils();

public:
    void initOsd(jint osdOffX, jint osdOffY, jint patternLen, jint frameWidth, jint frameHeight, jint rotation_angle);
    void addOsd(jbyte *nv21Src, jbyte *destData,const jchar *date);
    jbyteArray argbIntToNV21Byte( JNIEnv *env,jintArray ints,jint width, jint height);
    jbyteArray argbIntToNV12Byte(JNIEnv *env,jintArray ints,jint width, jint height);
    jbyteArray argbIntToGrayNVByte(JNIEnv *env,jintArray ints,jint width, jint height);
    void nv21ToNv12(jbyte *nv21Src,jbyte *nv12Dest, jint width, jint height);
    void releaseOsd();

private:
    int off_x, off_y;//x 偏移y 偏移
    jint num_width, num_height;//数字宽高
    int date_len, rotation;
    int frame_width, frame_height;
    char *mNumArrays;
    size_t size;
    bool is_init;//初始化标识
};

#ifdef __cplusplus
}
#endif
#endif //JRTPLIB_YUVUTILS_H
