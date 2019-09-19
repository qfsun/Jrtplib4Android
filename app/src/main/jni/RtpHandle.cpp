#include "com_wtoe_jrtplib_RtpHandle.h"
#include "RtpReceiver.h"
#include "RtpSender.h"

typedef struct {
    long sender;
    long receiver;
} RtpHandle;

JavaVM *g_jvm;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;//获取一个全局的VM指针
    logd("so库加载成功>>>>");
    return JNI_VERSION_1_4;
}

extern "C"
JNIEXPORT jlong JNICALL Java_com_wtoe_jrtplib_RtpHandle_initSendHandle
        (JNIEnv *env, jclass type, jint _localport, jstring _desthost, jint _destport,
         jobject listener) {
    //创建Handle
    RtpHandle *handle = new RtpHandle();

    //创建发送端
    CRTPSender *crtpSender = new CRTPSender;

    //转换远端IP
    const char *desthost = env->GetStringUTFChars(_desthost, 0);

    //初始化发送端参数
    bool flag = crtpSender->initParam(g_jvm,env,crtpSender, desthost, _localport, _destport,
                                      listener);
    //释放资源
    env->ReleaseStringUTFChars(_desthost, desthost);
    //如果初始化失败，删除资源
    if (!flag) {
        delete crtpSender;
        delete handle;
        return 0;
    }
    handle->sender = (jlong) crtpSender;
    logd("%s Sender.  addr:%ld\n", "InitSendHandle", (jlong) crtpSender);
    logd("%s Handle.  addr:%ld\n", "InitSendHandle", (jlong) handle);
    return (jlong) handle;
}

extern "C"
JNIEXPORT jlong JNICALL Java_com_wtoe_jrtplib_RtpHandle_initReceiveAndSendHandle
        (JNIEnv *env, jclass type, jstring _localhost, jint _localport, jstring _desthost,
         jint _destport, jobject listener) {
    //创建Handle
    RtpHandle *handle = new RtpHandle();

    //创建发送端
    CRTPSender *crtpSender = new CRTPSender;

    //转换远端IP
    const char *desthost = env->GetStringUTFChars(_desthost, 0);

    //初始化发送端参数
    bool flag = crtpSender->initParam(g_jvm,env,crtpSender, desthost, _localport + 2, _destport,
                                      listener);
    //释放资源
    env->ReleaseStringUTFChars(_desthost, desthost);

    //如果初始化失败，删除资源
    if (!flag) {
        delete crtpSender;
        delete handle;
        return 0;
    }
    handle->sender = (jlong) crtpSender;

    //创建接收端
    CRTPReceiver *crtpReceiver = new CRTPReceiver;

    //转换本地IP
    const char *localhost = env->GetStringUTFChars(_localhost, 0);

    //初始化接收端参数
    bool flag_receive = crtpReceiver->init(g_jvm,env,crtpReceiver, localhost, _localport,listener);
    //释放资源
    env->ReleaseStringUTFChars(_localhost, localhost);

    //如果初始化失败，删除资源
    if (!flag_receive) {
        delete crtpReceiver;
        delete handle;
        return 0;
    }
    handle->receiver = (jlong) crtpReceiver;
    return (jlong) handle;
}

int m_Count;

extern "C"
JNIEXPORT jboolean JNICALL Java_com_wtoe_jrtplib_RtpHandle_sendByte
        (JNIEnv *env, jclass type, jlong rtpHandler, jbyteArray _src, jint _byteLength,
         jboolean isSpsOrMarker, jboolean isRtpData) {
    //如果rtpHandler=0，直接返回，不处理
    if (rtpHandler == 0) {
        return false;
    }

    //强转拿到rtpHandler
    RtpHandle *handle = (RtpHandle *) rtpHandler;
    //如果强转值为null，删除对象资源，并返回
    if (handle == NULL) {
        delete handle;
        return false;
    }
    //如果不存在发送端，则返回
    if (handle->sender == 0) {
        logd("sender = 0 , send error!");
        return false;
    }

    if (m_Count % 100 == 0) {
        logd("RtpHandle sendByte sender addr : %d \n", handle->sender);
        m_Count = 0;
    }
    m_Count++;
    CRTPSender *crtpSender = (CRTPSender *) (handle->sender);
    jbyte *src = env->GetByteArrayElements(_src, NULL);
    if (isRtpData) {
        crtpSender->SendRtpData((unsigned char *) src, _byteLength, isSpsOrMarker);
    } else {
        crtpSender->SendH264Nalu((unsigned char *) src, _byteLength, isSpsOrMarker);
    }
    env->ReleaseByteArrayElements(_src, src, 0);
    return true;
}

extern "C"
JNIEXPORT jboolean JNICALL Java_com_wtoe_jrtplib_RtpHandle_finiHandle
        (JNIEnv *env, jclass type, jlong rtpHandler) {
    if (rtpHandler == 0) {
        return false;
    }
    RtpHandle *handle = (RtpHandle *) rtpHandler;
    if (handle == NULL) {
        delete handle;
        return false;
    }
    logd("%s Handle.  addr:%ld\n", "finiHandle",rtpHandler);
    if (handle->sender != 0) {
        logd("%s Sender.  addr:%ld\n", "finiHandle", handle->sender);
        CRTPSender *crtpSender = (CRTPSender *) (handle->sender);
        if (crtpSender != NULL) {
            crtpSender->fini();
        }
        handle->sender = 0;
        delete crtpSender;
    }
    if (handle->receiver != 0) {
        logd("%s Receiver.  addr:%ld\n", "finiHandle", handle->receiver);
        CRTPReceiver *crtpReceiver = (CRTPReceiver *) (handle->receiver);
        if (crtpReceiver != NULL) {
            crtpReceiver->fini(env);
        }
        handle->receiver = 0;
        delete crtpReceiver;
    }
    delete handle;
}

