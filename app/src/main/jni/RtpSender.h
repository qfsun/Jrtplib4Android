#ifndef JRTPLIB_RTP_SENDER_H
#define JRTPLIB_RTP_SENDER_H

#include <jni.h>

#include "jthread/jthread.h"
#include "jrtplib3/rtpsession.h"
#include "jrtplib3/rtpudpv4transmitter.h"
#include "jrtplib3/rtpsessionparams.h"
#include "jrtplib3/rtpipv4address.h"
#include "jrtplib3/rtpsourcedata.h"
#include "jrtplib3/rtppacket.h"
#include "jrtplib3/rtptimeutilities.h"
#include "send-callback.h"
#include <RtpCommon.h>

using namespace jrtplib;
class CRTPSender : public RTPSession {
protected:
    virtual void OnPollThreadStart(bool &stop);

    virtual void OnPollThreadStop();

    virtual void OnNewSource(RTPSourceData *srcdat);

    virtual void OnBYEPacket(RTPSourceData *srcdat);

    virtual void OnRTCPCompoundPacket(RTCPCompoundPacket *pack, const RTPTime &receivetime,
                              const RTPAddress *senderaddress);

public:
    bool initParam(JavaVM *vm,CRTPSender &sess, const char *destip, uint16_t PORT_BASE, uint16_t DST_PORT,JNIEnv *env,jobject listener);

    bool fini();

    void SendH264Nalu(unsigned char *m_h264Buf, int buflen, bool isSpsOrPps);

    void SendRtpData(unsigned char *m_rtpBuf, int buflen, bool isMarker);

    void SetParamsForSendingH264();
};

#endif //JRTPLIB_RTP_SENDER_H
