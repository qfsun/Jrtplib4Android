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
#include "RtpCommon.h"
#include <string.h>
#include <arpa/inet.h>

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
    bool initParam(JavaVM *vm,JNIEnv *env,CRTPSender *sess, const char *destip, uint16_t PORT_BASE, uint16_t DST_PORT,jobject listener);

    bool fini();

    bool SendH264Nalu(unsigned char *m_h264Buf, int buflen, bool isSpsOrPps);

    bool SendRtpData(unsigned char *m_rtpBuf, int buflen, bool isMarker,long lastTime);

    void SetParamsForSendingH264();

    int FindStartCode2(unsigned char *Buf);//查找开始字符0x000001

    int FindStartCode3(unsigned char *Buf);//查找开始字符0x00000001

private:
    typedef struct {
        //byte 0
        unsigned char TYPE:5;
        unsigned char NRI:2;
        unsigned char F:1;
    } NALU_HEADER; /**//* 1 BYTES */

    typedef struct {
        //byte 0
        unsigned char TYPE:5;
        unsigned char NRI:2;
        unsigned char F:1;
    } FU_INDICATOR; /**//* 1 BYTES */

    typedef struct {
        //byte 0
        unsigned char TYPE:5;
        unsigned char R:1;
        unsigned char E:1;
        unsigned char S:1;
    } FU_HEADER; /**//* 1 BYTES */

    NALU_HEADER *nalu_hdr;
    FU_INDICATOR *fu_ind;
    FU_HEADER *fu_hdr;

private:
    JavaVM *s_vm;
    JNIEnv *s_env;
    bool s_init;
    uint32_t s_remoteIp;
    uint16_t s_remotePort;
    int mCount = 0;
    jobject s_jobj = NULL;
    SendCallback *s_callback;
    bool s_isAttach;
};

#endif //JRTPLIB_RTP_SENDER_H
