#ifndef JRTPLIB_RTPREEIVER_H
#define JRTPLIB_RTPREEIVER_H

#include <jni.h>
#include <queue>

#include "jthread/jthread.h"
#include "jrtplib3/rtpsession.h"
#include"jrtplib3/rtpudpv4transmitter.h"
#include "jrtplib3/rtpsessionparams.h"
#include "jrtplib3/rtpipv4address.h"
#include "jrtplib3/rtpsourcedata.h"
#include "receive-callback.h"
#include "RtpCommon.h"

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
    bool init(JavaVM *vm,const char *destip, uint16_t PORT_BASE , JNIEnv *env, jobject listener);

    bool fini(JNIEnv *env);

private:
    void processSourceData(RTPSourceData *srcdat, const char *funcName, bool add);

    void processRtpPacket(const RTPPacket *pack);
};

#endif //JRTPLIB_RTPREEIVER_H
