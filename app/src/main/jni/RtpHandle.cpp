#include "RtpReceiver.h"
#include "RtpSender.h"

typedef struct {
    long sender;
    long receiver;
} RtpHandle;

JavaVM *g_jvm;
int m_Count;

jlong initSendHandle_
        (JNIEnv *env, jclass type, jint _localport, jstring _desthost, jint _destport,
         jobject listener) {
    //创建Handle
    RtpHandle *handle = new RtpHandle();

    //创建发送端
    CRTPSender *crtpSender = new CRTPSender;

    //转换远端IP
    const char *desthost = env->GetStringUTFChars(_desthost, 0);

    //初始化发送端参数
    bool flag = crtpSender->initParam(g_jvm, env, crtpSender, desthost, _localport, _destport,
                                      listener);
    //释放资源
    env->ReleaseStringUTFChars(_desthost, desthost);
    //如果初始化失败，删除资源
    if (!flag) {
        return 0;
    }
    handle->sender = (jlong) crtpSender;
    return (jlong) handle;
}

jlong initReceiveAndSendHandle_
        (JNIEnv *env, jclass type, jstring _localhost, jint _localreceiveport,
         jint _localsendport,
         jstring _desthost,
         jint _destport, jobject listener) {
    //创建Handle
    RtpHandle *handle = new RtpHandle();

    //创建发送端
    CRTPSender *crtpSender = new CRTPSender;

    //转换远端IP
    const char *desthost = env->GetStringUTFChars(_desthost, 0);

    //初始化发送端参数
    bool flag = crtpSender->initParam(g_jvm, env, crtpSender, desthost, _localsendport, _destport,
                                      listener);
    //释放资源
    env->ReleaseStringUTFChars(_desthost, desthost);

    //如果初始化失败，删除资源
    if (!flag) {
        return 0;
    }
    handle->sender = (jlong) crtpSender;

    //创建接收端
    CRTPReceiver *crtpReceiver = new CRTPReceiver;

    //转换本地IP
    const char *localhost = env->GetStringUTFChars(_localhost, 0);

    //初始化接收端参数
    bool flag_receive = crtpReceiver->init(g_jvm, env, crtpReceiver, localhost, _localreceiveport,
                                           listener);
    //释放资源
    env->ReleaseStringUTFChars(_localhost, localhost);

    //如果初始化失败，删除资源
    if (!flag_receive) {
        return 0;
    }
    handle->receiver = (jlong) crtpReceiver;
    return (jlong) handle;
}

jboolean sendByte_
        (JNIEnv *env, jclass type, jlong rtpHandler, jbyteArray _src, jint _byteLength,
         jboolean isSpsOrMarker, jboolean isRtpData, jlong lastTime) {
    //如果rtpHandler=0，直接返回，不处理
    if (rtpHandler == 0) {
        return (jboolean) (false);
    }
    //强转拿到rtpHandler
    RtpHandle *handle = (RtpHandle *) rtpHandler;

    //如果不存在发送端，则返回
    if (handle->sender == 0) {
        logd("sender = 0 , send error!");
        return (jboolean) false;
    }

    if (m_Count % 100 == 0) {
        logd("RtpHandle sendByte sender addr : %ld \n", handle->sender);
        m_Count = 0;
    }
    m_Count++;
    CRTPSender *crtpSender = (CRTPSender *) (handle->sender);
    jbyte *src = env->GetByteArrayElements(_src, NULL);
    if (isRtpData) {
        crtpSender->SendRtpData((unsigned char *) src, _byteLength, isSpsOrMarker, lastTime);
    } else {
        crtpSender->SendH264Nalu((unsigned char *) src, _byteLength, isSpsOrMarker);
    }
    env->ReleaseByteArrayElements(_src, src, 0);
    return (jboolean) true;
}

jboolean finiHandle_(JNIEnv *env, jclass type, jlong rtpHandler) {
    if (rtpHandler == 0) {
        return (jboolean) false;
    }
    RtpHandle *handle = (RtpHandle *) rtpHandler;
    logd("%s Handle.  addr: %ld\n", "finiHandle", (long) rtpHandler);
    if (handle->sender != 0) {
        logd("%s Sender.  addr:%ld\n", "finiHandle", handle->sender);
        CRTPSender *crtpSender = (CRTPSender *) (handle->sender);
        if (crtpSender != NULL) {
            crtpSender->fini();
        }
        delete crtpSender;
        handle->sender = 0;
    }
    if (handle->receiver != 0) {
        logd("%s Receiver.  addr:%ld\n", "finiHandle", handle->receiver);
        CRTPReceiver *crtpReceiver = (CRTPReceiver *) (handle->receiver);
        if (crtpReceiver != NULL) {
            crtpReceiver->fini(env);
        }
        delete crtpReceiver;
        handle->receiver = 0;
    }
    delete handle;
    return (jboolean) true;
}


/*
 * JNINativeMethod数组。
 * JNINativeMethod结构体包含三个元素。
 * 第一个元素：java中的方法名。
 * 第二个元素：方法签名。
 * 第三个元素：C/C++中对应方法的指针。
 */
JNINativeMethod methods[] = {
        {"initSendHandle",           "(ILjava/lang/String;ILcom/wtoe/test/RtpListener;)J",                    (void *) initSendHandle_},
        {"initReceiveAndSendHandle", "(Ljava/lang/String;IILjava/lang/String;ILcom/wtoe/test/RtpListener;)J", (void *) initReceiveAndSendHandle_},
        {"sendByte",                 "(J[BIZZJ)Z",                                                               (void *) sendByte_},
        {"finiHandle",               "(J)Z",                                                                     (void *) finiHandle_},
};

/**
 * 注册本地方法。成功返回0，否则返回负数。
 * @param pEnv
 * @return
 */
int registerNativeMethods(JNIEnv *pEnv) {
    jclass clazz = pEnv->FindClass("com/wtoe/jrtplib/RtpHandle");
    if (clazz == NULL) {
        logd("clazz == NULL ！");
    }
    //调用Env环境中的注册方法。
    // 第一个实参：clazz是注册方法的类的字节码。
    // 第二个实参：methods为JNINativeMethod结构体数组，
    // 第三个参数为注册方法的个数。
    return pEnv->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;//获取一个全局的VM指针
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
