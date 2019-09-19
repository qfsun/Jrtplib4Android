#include <RtpReceiver.h>

#define H264               96
#define SSRC           100

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
void callbackRtcp(JNIEnv *env, int r_type, uint32_t r_ip, ReceiveCallback *hid_callback) {
    if (NULL != env) {
        //没有主动调用停止，才可以回调数据
        if (r_type == 1) {
            char *string = changeIP(r_ip);
            env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_rtcp,
                                env->NewStringUTF(string));
        } else if (r_type == 2) {
            char *string = changeIP(r_ip);
            env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_bye,
                                env->NewStringUTF(string));
        }
    }
}

//回调java监听
void callbackRtp(JNIEnv *env, uint8_t *buff, int packet_length, bool isMarker,
                 ReceiveCallback *hid_callback) {
    if (NULL != env) {
        //没有主动调用停止，才可以回调数据
        jbyteArray jbarray = env->NewByteArray(packet_length);
        env->SetByteArrayRegion(jbarray, 0, packet_length, (jbyte *) buff);
        env->CallVoidMethod(hid_callback->jobj, hid_callback->jmid_rtp, jbarray,
                            packet_length, isMarker);
        env->DeleteLocalRef(jbarray);
    }
}

bool
CRTPReceiver::init(JavaVM *vm, JNIEnv *env, CRTPReceiver *receiver, const char *localip,
                   uint16_t PORT_BASE,
                   jobject listener) {
    loge("CRTPReceiver init jni!\n");
    if (m_init) {
        loge("CRTPReceiver init failed. already inited. \n");
        return false;
    }
    if (g_jobj == NULL) {
        g_jobj = env->NewGlobalRef(listener);
    }
    r_env = env;
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
    status = receiver->Create(sessparams, &transparams);
    if (status < 0) {
        loge("CRTPReceiver init failed. Create(sessparams, &transparams)\n");
        return false;
    }
    CheckError(status);

    m_init = true;
    receiver->SetDefaultTimestampIncrement(3600);/* 设置时间戳增加间隔 */
    loge("CRTPReceiver init jni ok.\n");
    return true;
}

bool CRTPReceiver::fini(JNIEnv *env) {
    loge("CRTPReceiver fini jni!\n");
    m_init = false;
//    if (g_jobj && env) {
//        env->DeleteGlobalRef(g_jobj);
//        loge("DeleteGlobalRef.\n");
//    }
    BYEDestroy(RTPTime(2, 0), 0, 0);
    loge("CRTPReceiver fini jni ok.\n");
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
    if (m_init && isAttach) {
        callbackRtcp(r_env, 1, rtcpIp, hid_callback);
    }
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
    int status = r_vm->AttachCurrentThread(&r_env, NULL);
    if (status < 0) {
        isAttach = false;
        r_env = NULL;
        loge("CRTPReceiver AttachCurrentThread Error!\n");
        return;
    }
    isAttach = true;
    loge("CRTPReceiver AttachCurrentThread Ok!\n");
}

void CRTPReceiver::OnPollThreadStop() {
    logd("CRTPReceiver  OnPollThreadStop\n");
    //如果线程没有停止，就调用Detach，会报错。
    isAttach = false;
    if (r_env != NULL) {
        int status = r_vm->DetachCurrentThread();
        if (status < 0) {
            loge("CRTPReceiver DetachCurrentThread Error!\n");
        } else {
            loge("CRTPReceiver DetachCurrentThread Ok!\n");
        }
        r_env = NULL;
    }
}

void CRTPReceiver::OnNewSource(RTPSourceData *srcdat) {
    processSourceData(srcdat, "CRTPReceiver OnNewSource", true);
}

void CRTPReceiver::OnRemoveSource(RTPSourceData *srcdat) {
    processSourceData(srcdat, "CRTPReceiver OnRemoveSource", false);
}

void CRTPReceiver::OnBYEPacket(RTPSourceData *srcdat) {
    processSourceData(srcdat, "CRTPReceiver OnBYEPacket", false);
}

void CRTPReceiver::processSourceData(RTPSourceData *srcdat, const char *funcName, bool add) {
    if (funcName == NULL) {
        loge("CRTPReceiver processSourceData error. \n");
        return;
    }
    if (!srcdat) {
        logd("%s.CRTPReceiver error \n", funcName);
        return;
    }
    if (srcdat->IsOwnSSRC()) {
        logd("%s CRTPReceiver error. is own ssrc \n", funcName);
        return;
    }
    uint32_t ip = 0;
    uint16_t port = 0;
    if (srcdat->GetRTPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s CRTPReceiver RTP. ip:0x%x port:%d\n", funcName, ip, port);
    } else if (srcdat->GetRTCPDataAddress() != 0) {
        const RTPIPv4Address *addr = (const RTPIPv4Address *) (srcdat->GetRTCPDataAddress());
        ip = addr->GetIP();
        port = addr->GetPort();
        logd("%s CRTPReceiver RTCP. ip:0x%x port:%d\n", funcName, ip, port);
        port = port - 1;
    } else {
        logd("%s CRTPReceiver RTP/RTCP. error \n", funcName);
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
        ret = AddDestination(dest);
    } else {
        if (m_init && isAttach) {
            callbackRtcp(r_env, 2, ip, hid_callback);
        }
        ret = DeleteDestination(dest);
    }

    if (ret < 0) {
        logd("%s %s error. ip:0x%x port:%d\n", funcName,
             (add ? "AddDestination" : "DeleteDestination"), ip, port);
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
            logd("CRTPReceiver processRtpPacket cur seq:%d. \n", seq);
        }
        if (m_firstSeq) {
            m_firstSeq = false;
            logd("CRTPReceiver processRtpPacket cur seq:%d. \n", seq);
        } else {
            uint16_t tmp = m_lastSeq;
            if (++tmp != seq) {
                logd("CRTPReceiver lost packet!! last seq:%d, cur seq:%d\n", m_lastSeq, seq);
            }
        }
        m_lastSeq = seq;
        if (pack->GetPayloadType() == H264 && m_init && isAttach) {
            //std::cout<<"Got H264 packet：êo " << rtppack.GetExtendedSequenceNumber() << " from SSRC " << srcdat.GetSSRC() <<std::endl;
            callbackRtp(r_env, pack->GetPayloadData(), pack->GetPayloadLength(), pack->HasMarker(),
                        hid_callback);
        }
    } else {
        logd("CRTPReceiver processRtpPacket pack err. \n");
    }
}
