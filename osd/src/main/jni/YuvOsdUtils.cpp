#include <jni.h>
#include <YuvUtils.h>
#include<android/log.h>

#define logd(...) __android_log_print(ANDROID_LOG_DEBUG, "wtoe_yuv_osd", __VA_ARGS__)

jlong initOsd_(JNIEnv *env, jclass type, jint osdOffX,
               jint osdOffY, jint patternLen, jint frameWidth,
               jint frameHeight, jint rotation_angle) {
    YuvUtils *utils = new YuvUtils;
    utils->initOsd(osdOffX, osdOffY, patternLen, frameWidth, frameHeight, rotation_angle);
    logd("initOsd");
    return (long) utils;
};

void
addOsd_(JNIEnv *env, jclass type, jlong yuvHander, jbyteArray yuv_in_data, jbyteArray yvu_out_data,
        jstring date_) {
    jbyte *nv21Src = env->GetByteArrayElements(yuv_in_data, NULL);
    jbyte *destData = env->GetByteArrayElements(yvu_out_data, NULL);
    const jchar *date = env->GetStringChars(date_, NULL);

    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        utils->addOsd(nv21Src, destData, date);
    }
    env->ReleaseByteArrayElements(yuv_in_data, nv21Src, 0);
    env->ReleaseByteArrayElements(yvu_out_data, destData, 0);
    env->ReleaseStringChars(date_, date);
}

jbyteArray argbIntToNV21Byte_(JNIEnv *env, jclass jclazz, jlong yuvHander, jintArray ints,
                              jint width, jint height) {
    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        return utils->argbIntToNV21Byte(env, ints, width, height);
    }
    return NULL;
}


jbyteArray argbIntToNV12Byte_(JNIEnv *env, jclass jclazz, jlong yuvHander, jintArray ints,
                              jint width, jint height) {
    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        return utils->argbIntToNV12Byte(env, ints, width, height);
    }
    return NULL;
}

jbyteArray argbIntToGrayNVByte_(JNIEnv *env, jclass jclazz, jlong yuvHander,
                                jintArray ints,
                                jint width, jint height) {
    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        return utils->argbIntToGrayNVByte(env, ints, width, height);
    }
    return NULL;
}

void nv21ToNv12_(JNIEnv *env, jclass type, jlong yuvHander, jbyteArray nv21Src_,
                 jbyteArray nv12Dest_, jint width, jint height) {
    jbyte *nv21Src = env->GetByteArrayElements(nv21Src_, NULL);
    jbyte *nv12Dest = env->GetByteArrayElements(nv12Dest_, NULL);
    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        utils->nv21ToNv12(nv21Src, nv12Dest, width, height);
    }
    env->ReleaseByteArrayElements(nv21Src_, nv21Src, 0);
    env->ReleaseByteArrayElements(nv12Dest_, nv12Dest, 0);
}

void releaseOsd_(JNIEnv *env, jclass type, jlong yuvHander) {
    if (yuvHander != 0) {
        YuvUtils *utils = (YuvUtils *) yuvHander;
        utils->releaseOsd();
        delete utils;
    }
    logd("releaseOsd");
}

/*
 * JNINativeMethod数组。
 * JNINativeMethod结构体包含三个元素。
 * 第一个元素：java中的方法名。
 * 第二个元素：方法签名。
 * 第三个元素：C/C++中对应方法的指针。
 */
JNINativeMethod methods[] = {
        {"initOsd",             "(IIIIII)J",                  (void *) initOsd_},
        {"releaseOsd",          "(J)V",                       (void *) releaseOsd_},
        {"addOsd",              "(J[B[BLjava/lang/String;)V", (void *) addOsd_},
        {"argbIntToNV21Byte",   "(J[III)[B",                  (void *) argbIntToNV21Byte_},
        {"argbIntToNV12Byte",   "(J[III)[B",                  (void *) argbIntToNV12Byte_},
        {"argbIntToGrayNVByte", "(J[III)[B",                  (void *) argbIntToGrayNVByte_},
        {"nv21ToNv12",          "(J[B[BII)V",                 (void *) nv21ToNv12_},
};

/**
 * 注册本地方法。成功返回0，否则返回负数。
 * @param pEnv
 * @return
 */
int registerNativeMethods(JNIEnv *pEnv) {
    jclass clazz = pEnv->FindClass("com/wtoe/osd/YuvOsdUtils");
    if (clazz == NULL) {
        logd("clazz == NULL ！");
    }
    //调用Env环境中的注册方法。
    // 第一个实参：clazz是注册方法的类的字节码。
    // 第二个实参：methods为JNINativeMethod结构体数组，
    // 第三个参数为注册方法的个数。
    return pEnv->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    //通过JavaVM获取JNIEnv，成功后返回JNI_OK
    jint result = vm->GetEnv((void **) &env, JNI_VERSION_1_4);
    if (result != JNI_OK || env == NULL) {
        logd("JNI_OnLoad error");
        return -1;
    }
    if (registerNativeMethods(env) < 0) {
        logd("Methods(env) < 0 error");
        return -1;
    }
    // 返回jni的版本
    return JNI_VERSION_1_4;
}