#ifndef JRTPLIB_RTPREEIVER_H
#define JRTPLIB_RTPREEIVER_H

#include <jni.h>

#include "jthread/jthread.h"
#include "JRTPLIB/src/rtpsession.h"
#include "JRTPLIB/src/rtpudpv4transmitter.h"
#include "JRTPLIB/src/rtpsessionparams.h"
#include "JRTPLIB/src/rtpipv4address.h"
#include "JRTPLIB/src/rtpsourcedata.h"
#include "receive-callback.h"
#include "RtpCommon.h"
#include "pstream.h"
#include <arpa/inet.h>

using namespace jrtplib;

class CRTPReceiver : public RTPSession {
protected:
    virtual void OnPollThreadStep();

    virtual void OnPollThreadStart(bool &stop);

    virtual void OnPollThreadStop();

    virtual void OnNewSource(RTPSourceData *srcdat);

    virtual void OnRemoveSource(RTPSourceData *srcdat);

    virtual void OnBYEPacket(RTPSourceData *srcdat);

    virtual void OnRTCPCompoundPacket(RTCPCompoundPacket *pack, const RTPTime &receivetime,
                                      const RTPAddress *senderaddress);

public:
    bool
    init(JavaVM *vm, JNIEnv *env, CRTPReceiver *receiver, const char *destip, uint16_t PORT_BASE,
         jobject listener);

    bool fini(JNIEnv *env);

private:
    void processSourceData(RTPSourceData *srcdat, const char *funcName, bool add);

    void processRtpPacket(const RTPPacket *pack);

private:
    JavaVM *r_vm;
    JNIEnv *r_env;
    bool m_init;
    jobject g_jobj = NULL;
    uint16_t m_lastSeq;
    bool m_firstSeq;
    uint16_t m_count;
    ReceiveCallback *hid_callback;
    bool isAttach;
};

#endif //JRTPLIB_RTPREEIVER_H
