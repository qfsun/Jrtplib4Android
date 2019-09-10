#include <RtpReceiver.h>
#include <queue>


#define H264               96
#define SSRC           100

JavaVM *r_vm;
JNIEnv *r_env;

CRTPReceiver sess;
bool m_init;
jobject g_jobj = NULL;

uint16_t m_lastSeq;
bool m_firstSeq;
bool m_pollThreadExited;
uint16_t m_count;

ReceiveCallback *hid_callback;

ReceiveCallback::ReceiveCallback(_JNIEnv *env, jobject obj) {
    jobj = obj;
    jclass clz = env->GetObjectClass(jobj);
    if (!clz) {
        loge("get jclass wrong");
        return;
    }
    jmid_rtp = env->GetMethodID(clz, "receiveRtpData", "([BIZ)V");
    if (!jmid_rtp) {
        loge("get jmethodID jmid_rtp wrong");
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
void callbackRtcp(int r_type, uint32_t r_ip, ReceiveCallback *hid_callback) {
    if (NULL != r_env) {
        if (m_init) {
            //没有主动调用停止，才可以回调数据
            if (r_type == 1) {
                char *string = changeIP(r_ip);
                r_env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_rtcp,
                                      r_env->NewStringUTF(string));
            } else if (r_type == 2) {
                char *string = changeIP(r_ip);
                r_env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_bye,
                                      r_env->NewStringUTF(string));
            }
        }
    }
}

//回调java监听
void callbackRtp(uint8_t *buff, int packet_length, bool isMarker, ReceiveCallback *hid_callback) {
    if (NULL == r_env && m_init) {
        int status = r_vm->GetEnv((void **) &r_env, JNI_VERSION_1_4);
        if (status < 0) {
            status = r_vm->AttachCurrentThread(&r_env, NULL);
            loge(" AttachCurrentThread !\n");
            if (status < 0) {
                r_env = NULL;
            }
        } else {
        }
    }
    if (NULL != r_env) {
        if (m_init) {
            //没有主动调用停止，才可以回调数据
            jbyteArray jbarray = r_env->NewByteArray(packet_length);
            r_env->SetByteArrayRegion(jbarray, 0, packet_length, (jbyte *) buff);
            r_env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_rtp, jbarray,packet_length,isMarker);
            r_env->DeleteLocalRef(jbarray);
        } else {
            //如果线程没有停止，就调用Detach，会报错。
            if (m_pollThreadExited) {
                loge(" DetachCurrentThread !\n");
                r_vm->DetachCurrentThread();
                r_env = NULL;
                return;
            }
        }
    }
}

bool CRTPReceiver::init(JavaVM *vm, const char *localip, uint16_t PORT_BASE, JNIEnv *env,
                        jobject listener) {
    loge("init jni!\n");
    if (m_init) {
        loge("CRTPReceiver init failed. already inited. \n");
        return false;
    }
    if (g_jobj == NULL) {
        g_jobj = env->NewGlobalRef(listener);
    }
    r_vm = vm;
    hid_callback = new ReceiveCallback(env, g_jobj);
    env->GetJavaVM(&r_vm);//保存全局变量

    int status;
    // Now, we'll create a RTP session, set the destination
    // and poll for incoming data.
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
    uint32_t local_ip = inet_addr(localip);
    local_ip = ntohl(local_ip);
    transparams.SetBindIP(local_ip);
    transparams.SetPortbase(PORT_BASE);
    status = sess.Create(sessparams, &transparams);
    if (status < 0) {
        loge("init failed.\n");
        return false;
    }
    CheckError(status);

    m_init = true;
    sess.SetDefaultTimestampIncrement(3600);/* 设置时间戳增加间隔 */
    loge("init jni ok.\n");
    return true;
}

bool CRTPReceiver::fini(JNIEnv *env) {
    loge("fini jni!\n");
    m_init = false;
//    if (g_jobj && env) {
//        env->DeleteGlobalRef(g_jobj);
//        loge("DeleteGlobalRef.\n");
//    }
    sess.BYEDestroy(RTPTime(2, 0), 0, 0);
    loge("fini jni ok.\n");
    return true;
}

void CRTPReceiver::OnRTCPCompoundPacket(RTCPCompoundPacket *pack, const RTPTime &receivetime,
                                        const RTPAddress *senderaddress) {
    //std::cout<<"Got RTCP packet from: "<<senderaddress<<std::endl;

    const RTPIPv4Address *addr = (const RTPIPv4Address *) (senderaddress);
    //r_rtcpIp = addr->GetIP();
    uint32_t rtcpIp = addr->GetIP();
    uint32_t port = addr->GetPort();
    logd(" OnRTCP. ip:0x%x port:%d\n", rtcpIp, port);
    callbackRtcp(1, rtcpIp, hid_callback);
}

void CRTPReceiver::OnPollThreadStep() {
    BeginDataAccess();
    // check incoming packets
    if (GotoFirstSourceWithData()) {
        do {
            RTPPacket *rtppack = NULL;
            while ((rtppack = GetNextPacket()) != NULL) {
                processRtpPacket(rtppack);
                DeletePacket(rtppack);
            }
        } while (GotoNextSourceWithData());
    }
    EndDataAccess();
}

void CRTPReceiver::OnPollThreadStart(bool &stop) {
    logd("CRTPReceiver  OnPollThreadStart\n");
    m_pollThreadExited = false;
}

void CRTPReceiver::OnPollThreadStop() {
    logd("CRTPReceiver  OnPollThreadStop\n");
    m_pollThreadExited = true;
    callbackRtp(NULL, 0, false, hid_callback);
}

void CRTPReceiver::OnNewSource(RTPSourceData *srcdat) {
    processSourceData(srcdat, "OnNewSource", true);
}

void CRTPReceiver::OnRemoveSource(RTPSourceData *srcdat) {
    processSourceData(srcdat, "OnRemoveSource", false);
}

void CRTPReceiver::OnBYEPacket(RTPSourceData *srcdat) {
    processSourceData(srcdat, "OnBYEPacket", false);
}

void CRTPReceiver::processSourceData(RTPSourceData *srcdat, const char *funcName, bool add) {
    if (funcName == NULL) {
        loge("processSourceData error. \n");
        return;
    }
    if (!srcdat) {
        logd("%s. error \n", funcName);
        return;
    }
    if (srcdat->IsOwnSSRC()) {
        logd("%s error. is own ssrc \n", funcName);
        return;
    }
    uint32_t ip = 0;
    uint16_t port = 0;
    if (srcdat->GetRTPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s RTP. ip:0x%x port:%d\n", funcName, ip, port);
    } else if (srcdat->GetRTCPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTCPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s RTCP. ip:0x%x port:%d\n", funcName, ip, port);
        port = port - 1;
    } else {
        logd("%s RTP/RTCP. error \n", funcName);
        return;
    }
//    if (ip != r_remoteIp || port != r_remotePort) {
//        logd("%s-- msg-ip:0x%x port:%d remote-ip:0x%x port:%d\n", funcName, ip, port,
//             r_remoteIp, r_remotePort);
//        return;
//    }
    RTPIPv4Address dest(ip, port);
    int32_t ret = 0;
    if (add) {
        ret = this->AddDestination(dest);
    } else {
        callbackRtcp(2, ip, hid_callback);
        ret = this->DeleteDestination(dest);
    }

    if (ret < 0) {
        logd("%s %s error:%s. ip:0x%x port:%d\n", funcName,
             (add ? "AddDestination" : "DeleteDestination"), RTPGetErrorString(ret).c_str(), ip,
             port);
    } else {
        logd("%s %s ok. ip:0x%x port:%d\n", funcName,
             (add ? "AddDestination" : "DeleteDestination"), ip, port);
    }
}

void CRTPReceiver::processRtpPacket(const RTPPacket *pack) {
    if (pack) {
        uint16_t seq = pack->GetSequenceNumber();
        if (0 == ((++m_count) % 500)) {
            m_count = 0;
            logd("processRtpPacket cur seq:%d. \n", seq);
        }
        if (m_firstSeq) {
            m_firstSeq = false;
            logd("processRtpPacket cur seq:%d. \n", seq);
        } else {
            uint16_t tmp = m_lastSeq;
            if (++tmp != seq) {
                logd("lost packet!! last seq:%d, cur seq:%d\n", m_lastSeq, seq);
            }
        }
        m_lastSeq = seq;
        if (pack->GetPayloadType() == H264) {
            //std::cout<<"Got H264 packet：êo " << rtppack.GetExtendedSequenceNumber() << " from SSRC " << srcdat.GetSSRC() <<std::endl;
            callbackRtp(pack->GetPayloadData(), pack->GetPayloadLength(), pack->HasMarker(),
                        hid_callback);
        }
    } else {
        logd("processRtpPacket pack err. \n");
    }
}
