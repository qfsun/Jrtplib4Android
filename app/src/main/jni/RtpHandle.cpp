#include "com_wtoe_jrtplib_RtpHandle.h"
#include "RtpReceiver.h"
#include "RtpSender.h"

CRTPReceiver *m_receive;
CRTPSender m_send;
bool isSend;
bool isReceive;
bool isHandle;

JavaVM *g_jvm;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;//获取一个全局的VM指针
    logd("so库加载成功>>>>");
    return JNI_VERSION_1_4;
}

JNIEXPORT jboolean
JNICALL Java_com_wtoe_jrtplib_RtpHandle_initSendHandle
        (JNIEnv *env, jclass type, jint localport, jstring desthost_, jint destport,
         jobject listener) {
    if (isSend) {
        logd("Send Handle already init!");
        return true;
    }
    const char *host = env->GetStringUTFChars(desthost_, 0);

    isSend = m_send.initParam(g_jvm, m_send, host, localport, destport, env, listener);
    env->ReleaseStringUTFChars(desthost_, host);
    return isSend;
}

JNIEXPORT jboolean
JNICALL Java_com_wtoe_jrtplib_RtpHandle_initReceiveHandle
        (JNIEnv *env, jclass type, jstring localhost_, jint localport, jobject listener) {
    if (isReceive) {
        logd("Receive Handle already init!");
        return true;
    }
    const char *host = env->GetStringUTFChars(localhost_, 0);

    isReceive = m_receive->init(g_jvm, host, localport, env, listener);
    env->ReleaseStringUTFChars(localhost_, host);
    return isReceive;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtoe_jrtplib_RtpHandle_initReceiveAndSendHandle(JNIEnv *env, jclass type,
                                                            jstring localhost_, jint localport,
                                                            jstring desthost_, jint destport,
                                                            jobject listener) {
    if (isHandle) {
        logd("ReceiveAndSend Handle  already init!");
        return true;
    }
    const char *localhost = env->GetStringUTFChars(localhost_, 0);
    const char *desthost = env->GetStringUTFChars(desthost_, 0);

    // TODO
    //发送端也需要占用端口，不能与接收端一致
    isSend = m_send.initParam(g_jvm, m_send, desthost, localport + 2, destport, env, listener);
    isReceive = m_receive->init(g_jvm, localhost, localport, env, listener);
    env->ReleaseStringUTFChars(localhost_, localhost);
    env->ReleaseStringUTFChars(desthost_, desthost);

    if (isSend && isReceive) {
        isHandle = true;
    }
    return isHandle;
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_wtoe_jrtplib_RtpHandle_sendH264Byte(JNIEnv *env, jclass type, jbyteArray src_,
                                                jint byteLength, jboolean isSpsOrPps) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    // TODO
    if (isSend) {
        m_send.SendH264Nalu((unsigned char *) (src), byteLength, isSpsOrPps);
    } else {
        logd("Send Handle not init!");
        env->ReleaseByteArrayElements(src_, src, 0);
        return false;
    }
    env->ReleaseByteArrayElements(src_, src, 0);
    return true;
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_wtoe_jrtplib_RtpHandle_sendRtpByte(JNIEnv *env, jclass type, jbyteArray src_,
                                               jint byteLength, jboolean isMarker) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    // TODO
    if (isSend) {
        m_send.SendRtpData((unsigned char *) (src), byteLength, isMarker);
    } else {
        logd("Send Handle not init!");
        env->ReleaseByteArrayElements(src_, src, 0);
        return false;
    }
    env->ReleaseByteArrayElements(src_, src, 0);
    return true;
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_wtoe_jrtplib_RtpHandle_finiRtp(JNIEnv *env, jclass type) {
    // TODO
    if (isSend) {
        m_send.fini();
        isSend = false;
    }
    if (isReceive) {
        m_receive->fini(env);
        isReceive = false;
    }
    if (isHandle) {
        isHandle = false;
    }
    return true;
}