#include <RtmpUtil.h>

/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    connect
 * Signature: (Ljava/lang/String;)I
  * 初始化RTMP连接
 */
jlong initRtmpHandle_(JNIEnv *env, jobject type, jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    RtmpUtils *utils = new RtmpUtils();
    int ret = utils->initRtmpHandle(url);
    env->ReleaseStringUTFChars(url_, url);
    logd("initRtmpHandle");
    if (ret == 0) {
//        如果初始化成功，则返回数据对象
        return (long) utils;
    } else {
//        初始化失败，则返回0
        return 0;
    }
}

pthread_mutex_t mute_x;//线程锁

/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    push
 * Signature: ([BI)I
  * 推送数据
 */
jint pushSPSPPS_(JNIEnv *env, jobject thiz, jlong rtmpHander, jbyteArray sps_, jint sps_len,
                 jbyteArray pps_, jint pps_len) {
    if (rtmpHander != 0) {
        pthread_mutex_lock(&mute_x);
        RtmpUtils *utils = (RtmpUtils *) rtmpHander;
        jbyte *sps = env->GetByteArrayElements(sps_, NULL);
        jbyte *pps = env->GetByteArrayElements(pps_, NULL);
        int ret = 0;
        if (sps != NULL && pps_ != NULL) {
            ret = utils->PushSPSPPS(reinterpret_cast<const unsigned char *>(sps), sps_len,
                                    reinterpret_cast<const unsigned char *>(pps), pps_len);
        }
        env->ReleaseByteArrayElements(sps_, sps, 0);
        env->ReleaseByteArrayElements(pps_, pps, 0);
        pthread_mutex_unlock(&mute_x);
        return ret;
    }
    return 0;
}

/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    push
 * Signature: ([BI)I
  * 推送数据
 */
jint pushH264Data_(JNIEnv *env, jobject type, jlong rtmpHander, jbyteArray buf_, jint length,
                   jboolean is_Key) {
    if (rtmpHander != 0) {
        pthread_mutex_lock(&mute_x);
        RtmpUtils *utils = (RtmpUtils *) rtmpHander;
        jbyte *buf = env->GetByteArrayElements(buf_, NULL);
        int len = env->GetArrayLength(buf_);
        int ret = 0;
        if (buf != NULL) {
            ret = utils->PushH264Data(reinterpret_cast<unsigned char *>(buf), len, is_Key);
        }
        env->ReleaseByteArrayElements(buf_, buf, 0);
        pthread_mutex_unlock(&mute_x);
        return ret;
    }
    return 0;
}


/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    push
 * Signature: ([BI)I
  * 推送数据
 */
jint pushFlvData_(JNIEnv *env, jobject type, jlong rtmpHander, jbyteArray buf_, jint length) {
    if (rtmpHander != 0) {
        pthread_mutex_lock(&mute_x);
        RtmpUtils *utils = (RtmpUtils *) rtmpHander;
        jbyte *buf = env->GetByteArrayElements(buf_, NULL);
        int len = env->GetArrayLength(buf_);
        int ret = 0;
        if (buf != NULL) {
            ret = utils->pushFlvData((unsigned char *) (buf), len);
        }
        env->ReleaseByteArrayElements(buf_, buf, 0);
        pthread_mutex_unlock(&mute_x);
        return ret;
    }
    return 0;
}

/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    push
  * 推送flv文件
 */
jint pushFlvFile_(JNIEnv *env, jobject type, jstring url_, jstring path_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    const char *path = env->GetStringUTFChars(path_, 0);
    RtmpUtils *utils = new RtmpUtils();
    int ret = utils->pushFlvFile(url, path);
    delete utils;
    env->ReleaseStringUTFChars(url_, url);
    env->ReleaseStringUTFChars(path_, path);
    return ret;
}

/*
 * Class:     com_wtoe_rtmp_RtmpHandle
 * Method:    close
 * Signature: ()I
 */
void releaseRtmpHandle_(JNIEnv *env, jobject type, jlong rtmpHander) {
    if (rtmpHander != 0) {
        logd("releaseRtmpHandle . rtmpHander != 0");
        RtmpUtils *utils = (RtmpUtils *) rtmpHander;
        utils->releaseRtmpHandle();
        delete utils;
    }
    logd("releaseRtmpHandle");
}

/*
 * JNINativeMethod数组。
 * JNINativeMethod结构体包含三个元素。
 * 第一个元素：java中的方法名。
 * 第二个元素：方法签名。
 * 第三个元素：C/C++中对应方法的指针。
 */
JNINativeMethod methods[] = {
        {"initRtmpHandle",    "(Ljava/lang/String;)J",                   (void *) initRtmpHandle_},
        {"releaseRtmpHandle", "(J)V",                                    (void *) releaseRtmpHandle_},
        {"pushFlvData",       "(J[BI)I",                                 (void *) pushFlvData_},
        {"pushH264Data",      "(J[BIZ)I",                                (void *) pushH264Data_},
        {"pushSPSPPS",        "(J[BI[BI)I",                              (void *) pushSPSPPS_},
        {"pushFlvFile",       "(Ljava/lang/String;Ljava/lang/String;)I", (void *) pushFlvFile_},
};

/**
 * 注册本地方法。成功返回0，否则返回负数。
 * @param pEnv
 * @return
 */
int registerNativeMethods(JNIEnv *pEnv) {
    jclass clazz = pEnv->FindClass("com/wtoe/rtmp/RtmpHandle");
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
