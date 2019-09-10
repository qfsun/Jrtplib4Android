#include <RtpSender.h>
#include "RtpSender.h"


#define MAX_RTP_PKT_LENGTH 1300
#define SEND_LENGTH 1400
#define H264               96
#define SSRC           100

bool s_init;
uint32_t s_remoteIp;
uint16_t s_remotePort;
int mCount = 0;

JavaVM *s_vm;
JNIEnv *s_env;
jobject s_jobj = NULL;
bool s_pollThreadExited;

SendCallback *s_callback;

SendCallback::SendCallback(_JNIEnv *env, jobject obj) {
    jobj = obj;
    jclass clz = env->GetObjectClass(jobj);
    if (!clz) {
        loge("get jclass wrong");
        return;
    }
    jmid_rtcp = env->GetMethodID(clz, "receiveRtcpData", "(Ljava/lang/String;)V");
    if (!jmid_rtcp) {
        loge("get jmethodID jmid_rtcp wrong");
        return;
    }
    jmid_bye = env->GetMethodID(clz, "receiveBye", "(Ljava/lang/String;)V");
    if (!jmid_bye) {
        loge("get jmethodID jmid_bye wrong");
        return;
    }
}

//回调java监听
void callbackRtcp(int r_type, uint32_t r_ip, SendCallback *hid_callback) {
    if (NULL == s_env && s_init) {
        int status = s_vm->GetEnv((void **) &s_env, JNI_VERSION_1_4);
        if (status < 0) {
            status = s_vm->AttachCurrentThread(&s_env, NULL);
            loge(" AttachCurrentThread !\n");
            if (status < 0) {
                s_env = NULL;
            }
        } else {
        }
    }
    if (NULL != s_env) {
        if (s_init) {
            //没有主动调用停止，才可以回调数据
            if (r_type == 1) {
                char *string = changeIP(r_ip);
                s_env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_rtcp,
                                      s_env->NewStringUTF(string));
            } else if (r_type == 2) {
                char *string = changeIP(r_ip);
                s_env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_bye,
                                      s_env->NewStringUTF(string));
            }
        } else {
            //如果线程没有停止，就调用Detach，会报错。
            if (s_pollThreadExited) {
                loge(" DetachCurrentThread !\n");
                s_vm->DetachCurrentThread();
                s_env = NULL;
                return;
            }
        }
    }
}

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

int FindStartCode2(unsigned char *Buf);//查找开始字符0x000001
int FindStartCode3(unsigned char *Buf);//查找开始字符0x00000001

void CRTPSender::OnRTCPCompoundPacket(RTCPCompoundPacket *pack, const RTPTime &receivetime,
                                      const RTPAddress *senderaddress) {
    const RTPIPv4Address *addr = (const RTPIPv4Address *) (senderaddress);
    //r_rtcpIp = addr->GetIP();
    uint32_t rtcpIp = addr->GetIP();
    uint32_t port = addr->GetPort();
    logd(" OnRTCP. ip:0x%x port:%d\n", rtcpIp, port);
    callbackRtcp(1, rtcpIp, s_callback);
}

bool
CRTPSender::initParam(JavaVM *vm, CRTPSender &sess, const char *host, uint16_t PORT_BASE,
                      uint16_t DST_PORT, JNIEnv *env, jobject listener) {
    logd("init jni!\n");
    if (s_init) {
        loge("CRTPReceiver already init. \n");
        return false;
    }
    if (s_jobj == NULL) {
        s_jobj = env->NewGlobalRef(listener);
    }
    s_vm = vm;
    s_callback = new SendCallback(env, s_jobj);
    env->GetJavaVM(&s_vm);//保存全局变量

    int status;
    //RTP+RTCP库初始化SOCKET环境
    RTPUDPv4TransmissionParams transparams;
    RTPSessionParams sessparams;

    if (sessparams.SetUsePollThread(true) < 0) {
        loge("CRTPReceiver init failed. SetUsePollThread error. \n");
        return false;
    }
    /* set h264 param */
    sessparams.SetUsePredefinedSSRC(true);  //设置使用预先定义的SSRC
    sessparams.SetOwnTimestampUnit(1.0 / 9000.0); /* 设置采样间隔 */
    sessparams.SetAcceptOwnPackets(false);   //接收自己发送的数据包
    sessparams.SetPredefinedSSRC(SSRC);     //定义SSRC

    transparams.SetPortbase(PORT_BASE);

    status = sess.Create(sessparams, &transparams);
    CheckError(status);

    uint32_t destip = inet_addr(host);
    destip = ntohl(destip);
    s_remoteIp = destip;
    s_remotePort = DST_PORT;
    RTPIPv4Address addr(destip, DST_PORT);
    status = sess.AddDestination(addr);
    CheckError(status);
    s_init = true;
    //为发送H264包设置参数
    sess.SetParamsForSendingH264();
    logd("init jni ok.\n");
    return true;
}

bool CRTPSender::fini() {
    loge("fini jni!\n");
    RTPIPv4Address addr(s_remoteIp, s_remotePort);
    DeleteFromAcceptList(addr);
    s_init = false;
    BYEDestroy(RTPTime(10, 0), 0, 0);
    loge("fini jni ok.\n");
    return true;
}

void CRTPSender::OnNewSource(RTPSourceData *srcdat) {
    uint32_t ip = 0;
    uint16_t port = 0;
    if (srcdat->GetRTPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s RTP. ip:0x%x port:%d\n", "OnNewSource", ip, port);
    } else if (srcdat->GetRTCPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTCPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s RTCP. ip:0x%x port:%d\n", "OnNewSource", ip, port);
        port = port - 1;
    } else {
        logd("%s RTP/RTCP. error \n", "OnNewSource");
        return;
    }
    if (ip != s_remoteIp || port != s_remotePort) {
        logd("%s-- msg-ip:0x%x port:%d remote-ip:0x%x port:%d\n", "OnNewSource", ip, port,
             s_remoteIp, s_remotePort);
        return;
    }
}

void CRTPSender::OnPollThreadStart(bool &stop) {
    logd("CRTPReceiver  OnPollThreadStart\n");
    s_pollThreadExited = false;
}

void CRTPSender::OnPollThreadStop() {
    logd("CRTPReceiver  OnPollThreadStop\n");
    s_pollThreadExited = true;
    callbackRtcp(0, false, s_callback);
}

void CRTPSender::SendH264Nalu(unsigned char *m_h264Buf, int buflen, bool isSpsOrPps) {
    if (buflen < 4) {
        return;
    }
    unsigned char *pSendbuf; //发送数据指针
    pSendbuf = m_h264Buf;
    //去除前导码0x000001 或者0x00000001
    if (FindStartCode2(m_h264Buf) == 1) {
        pSendbuf = &m_h264Buf[3];
        buflen -= 3;
    } else if (FindStartCode3(m_h264Buf) == 1) {
        pSendbuf = &m_h264Buf[4];
        buflen -= 4;
    }
    char sendbuf[SEND_LENGTH];
    memset(sendbuf, 0, SEND_LENGTH);
    int status;
    if (mCount % 100 == 0) {
        logd("SendH264Nalu packet length %d \n", buflen);
        mCount = 0;
    }
    mCount++;
    if (isSpsOrPps) {
        this->SetDefaultMark(false);
        memcpy(sendbuf, pSendbuf, buflen);
        status = this->SendPacket((void *) sendbuf, buflen);
        CheckError(status);
        return;
    }
    if (buflen <= MAX_RTP_PKT_LENGTH) {
        this->SetDefaultMark(true);
        //设置NALU HEADER,并将这个HEADER填入sendbuf[0]
        nalu_hdr = (NALU_HEADER *) &sendbuf[0]; //将sendbuf[0]的地址赋给nalu_hdr，之后对nalu_hdr的写入就将写入sendbuf中；
        nalu_hdr->F = (char) (pSendbuf[0] & 0x80);
        nalu_hdr->NRI = (char) ((pSendbuf[0] & 0x60)
                >> 5);//有效数据在n->nal_reference_idc的第6，7位，需要右移5位才能将其值赋给nalu_hdr->NRI。
        nalu_hdr->TYPE = (char) (pSendbuf[0] & 0x1f);
        //NALU头已经写到sendbuf[0]中，接下来则存放的是NAL的第一个字节之后的数据。所以从r的第二个字节开始复制
        memcpy(sendbuf + 1, pSendbuf, buflen);
        status = this->SendPacket((void *) sendbuf, buflen);
        CheckError(status);
    } else if (buflen >= MAX_RTP_PKT_LENGTH) {
        this->SetDefaultMark(false);
        //得到该nalu需要用多少长度为1400字节的RTP包来发送
        int k = 0, l = 0;
        k = buflen / MAX_RTP_PKT_LENGTH;//需要k个1400字节的RTP包，这里为什么不加1呢？因为是从0开始计数的。
        l = buflen % MAX_RTP_PKT_LENGTH;//最后一个RTP包的需要装载的字节数
        int t = 0;//用于指示当前发送的是第几个分片RTP包
        while (t < k || (t == k && l > 0)) {
            memset(sendbuf, 0, SEND_LENGTH);
            if (t == 0) {
                //第一片
                //设置FU INDICATOR,并将这个HEADER填入sendbuf[0]
                fu_ind = (FU_INDICATOR *) &sendbuf[0]; //将sendbuf[0]的地址赋给fu_ind，之后对fu_ind的写入就将写入sendbuf中；
                fu_ind->F = (char) (pSendbuf[0] & 0x80);
                fu_ind->NRI = (char) ((pSendbuf[0] & 0x60) >> 5);
                fu_ind->TYPE = 28;  //FU-A类型。
                //设置FU HEADER,并将这个HEADER填入sendbuf[1]
                fu_hdr = (FU_HEADER *) &sendbuf[1];
                fu_hdr->E = 0;
                fu_hdr->R = 0;
                fu_hdr->S = 1;
                fu_hdr->TYPE = (char) (pSendbuf[0] & 0x1f);
                memcpy(sendbuf + 2, &pSendbuf[t * MAX_RTP_PKT_LENGTH + 1], MAX_RTP_PKT_LENGTH);
                status = this->SendPacket((void *) sendbuf, MAX_RTP_PKT_LENGTH + 2, H264, false, 0);
                CheckError(status);
                t++;
            } else if (t < k)//既不是第一片，也不是最后一片
            {
                //设置FU INDICATOR,并将这个HEADER填入sendbuf[0]
                fu_ind = (FU_INDICATOR *) &sendbuf[0]; //将sendbuf[0]的地址赋给fu_ind，之后对fu_ind的写入就将写入sendbuf中；
                fu_ind->F = (char) (pSendbuf[0] & 0x80);
                fu_ind->NRI = (char) ((pSendbuf[0] & 0x60) >> 5);
                fu_ind->TYPE = 28;
                //设置FU HEADER,并将这个HEADER填入sendbuf[1]
                fu_hdr = (FU_HEADER *) &sendbuf[1];
                fu_hdr->R = 0;
                fu_hdr->S = 0;
                fu_hdr->E = 0;
                fu_hdr->TYPE = (char) (pSendbuf[0] & 0x1f);
                memcpy(sendbuf + 2, &pSendbuf[t * MAX_RTP_PKT_LENGTH + 1], MAX_RTP_PKT_LENGTH);
                status = this->SendPacket((void *) sendbuf, MAX_RTP_PKT_LENGTH + 2, H264, false, 0);
                CheckError(status);
                t++;
            }
                //最后一包
            else if (k == t) {
                this->SetDefaultMark(true);
                int iSendLen;
                if (l > 0) {
                    iSendLen = buflen - t * MAX_RTP_PKT_LENGTH;
                } else {
                    iSendLen = MAX_RTP_PKT_LENGTH;
                }
                //设置FU INDICATOR,并将这个HEADER填入sendbuf[0]
                fu_ind = (FU_INDICATOR *) &sendbuf[0]; //将sendbuf[0]的地址赋给fu_ind，之后对fu_ind的写入就将写入sendbuf中；
                fu_ind->F = (char) (pSendbuf[0] & 0x80);
                fu_ind->NRI = (char) ((pSendbuf[0] & 0x60) >> 5);
                fu_ind->TYPE = 28;  //FU-A类型。
                //设置FU HEADER,并将这个HEADER填入sendbuf[1]
                fu_hdr = (FU_HEADER *) &sendbuf[1];
                fu_hdr->R = 0;
                fu_hdr->S = 0;
                fu_hdr->TYPE = (char) (pSendbuf[0] & 0x1f);
                fu_hdr->E = 1;
                memcpy(sendbuf + 2, &pSendbuf[t * MAX_RTP_PKT_LENGTH + 1], iSendLen - 1);
                status = this->SendPacket((void *) sendbuf, iSendLen - 1 + 2, H264, true, 3600);
                CheckError(status);
                t++;
            }
        }
    }
}

//转发rtp数据
void CRTPSender::SendRtpData(unsigned char *m_rtpBuf, int buflen, bool isMarker) {
    if (buflen < 4) {
        return;
    }
    if (mCount % 100 == 0) {
        logd("SendRtpData packet length %d \n", buflen);
        mCount = 0;
    }
    int status;
    this->SetMaximumPacketSize(1500);
    if (isMarker) {
        status = this->SendPacket((void *) m_rtpBuf, buflen, H264, isMarker, 3600);
        CheckError(status);
    } else {
        status = this->SendPacket((void *) m_rtpBuf, buflen, H264, isMarker, 0);
        CheckError(status);
    }
}

void CRTPSender::SetParamsForSendingH264() {
    this->SetDefaultPayloadType(H264);//设置传输类型
    this->SetDefaultMark(true);        //设置位
    this->SetTimestampUnit(1.0 / 9000.0); //设置采样间隔
    this->SetDefaultTimestampIncrement(3600);//设置时间戳增加间隔
}

void CRTPSender::OnBYEPacket(RTPSourceData *srcdat) {
    logd("OnBYEPacket >>>> ");
    uint32_t ip = 0;
    uint16_t port = 0;
    if (srcdat->GetRTPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("RTP. ip:0x%x port:%d\n", ip, port);
    } else if (srcdat->GetRTCPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTCPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("RTCP. ip:0x%x port:%d\n", ip, port);
        port = port - 1;
    } else {
        logd("%s RTP/RTCP. error \n", "OnBYEPacket");
        return;
    }
    callbackRtcp(2, ip, s_callback);
}

int FindStartCode2(unsigned char *Buf) {
    if (Buf[0] != 0 || Buf[1] != 0 || Buf[2] != 1) return 0; //判断是否为0x000001,如果是返回1
    else return 1;
}

int FindStartCode3(unsigned char *Buf) {
    if (Buf[0] != 0 || Buf[1] != 0 || Buf[2] != 0 || Buf[3] != 1) return 0;//判断是否为0x00000001,如果是返回1
    else return 1;
}
