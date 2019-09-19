package com.wtoe.test;

import android.content.Context;

import com.wtoe.jrtplib.RtpHandle;
import com.wtoe.test.rtsp.RTSPClient;
import com.wtoe.test.rtsp.RtspEventListener;
import com.wtoe.test.rtsp.RtspStatus;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * author：create by Administrator on 2019/9/19
 * email：1564063766@qq.com
 * remark:
 */
public class IpCameraHelper implements RtpListener, RtspEventListener {
    private static final String TAG = IpCameraHelper.class.getSimpleName();
    private RTSPClient rtspClient;
    private boolean isRunning = false;

    private RtpHandle rtpHandle;
    private long mRtpHandle;
    private String localIP, remoteIp;
    private int localPort, remotePort;

    class RtpData {
        private byte[] data;
        private int length;
        private boolean isMark;

        public RtpData(byte[] data, int length, boolean isMark) {
            this.data = data;
            this.length = length;
            this.isMark = isMark;
        }
    }

    private int queueSize = 30;
    private ArrayBlockingQueue<RtpData> rtpDataQueue = new ArrayBlockingQueue<>(queueSize);

    /**
     * 构造
     *
     * @param localIP
     * @param localPort
     * @param remoteIp
     * @param remotePort
     */
    public IpCameraHelper(String localIP, int localPort, String remoteIp, int remotePort) {
        this.localIP = localIP;
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
    }

    /**
     * 收发数据
     */
    public void initData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                isRunning = true;
                rtpHandle = new RtpHandle();
                LogUtil.d(TAG, "localIp:" + localIP + "  / localPort:" + localPort);
                LogUtil.d(TAG, "remoteIp:" + remoteIp + "  / remotePort:" + remotePort);

                int mport = rtpHandle.getAvailablePort(localPort);

                mRtpHandle = rtpHandle.initReceiveAndSendHandle(localIP, mport, remoteIp, remotePort, IpCameraHelper.this);
                if (mRtpHandle == 0) {
                    rtpHandle = null;
                    LogUtil.d(TAG, "JNI init error");
                    return;
                }
                LogUtil.d(TAG, "JNI init ok");
                RtpData rtpData;
                while (isRunning) {
                    rtpData = rtpDataQueue.poll();
                    if (rtpData != null && rtpHandle != null && mRtpHandle != 0) {
                        rtpHandle.sendByte(mRtpHandle, rtpData.data, rtpData.length, rtpData.isMark, true);
                    }
                    rtpData = null;
                }
            }
        }).start();
    }

    /**
     * 初始化RTSP客户端调试
     */
    public void initRtspClient(final String rtsp) {
        if (rtsp.isEmpty() || !rtsp.startsWith("rtsp://")) {
            LogUtil.e(TAG, "rtsp地址异常");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //rtsp://192.168.2.195:554/av0_0
                    String tmp = rtsp.substring(7);
                    String[] tmp1 = tmp.split("/");
                    String[] tmp2 = tmp1[0].split(":");
                    rtspClient = new RTSPClient(new InetSocketAddress(
                            tmp2[0], Integer.parseInt(tmp2[1])),
                            new InetSocketAddress(localIP, 0),
                            rtsp, IpCameraHelper.this);
                    rtspClient.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void fini() {
        isRunning = false;
        if (rtpHandle != null && mRtpHandle != 0) {
            rtpHandle.finiHandle(mRtpHandle);
            rtpHandle = null;
            mRtpHandle = 0;
        }
        if (rtspClient != null) {
            rtspClient.doTeardown();
            rtspClient.shutdown();
            rtspClient = null;
        }
    }

    private int mCount = 0;

    @Override
    public void receiveRtpData(byte[] rtp_data, int pkg_size, boolean isMarker) {
        if (mCount % 100 == 0) {
            mCount = 0;
            System.out.println("收到 【RTP】" + pkg_size);
        }
        mCount++;
        addDataToQueue(new RtpData(rtp_data, pkg_size, isMarker));
    }

    private void addDataToQueue(RtpData rtpData) {
        try {
            if (rtpDataQueue.size() >= queueSize) {
                rtpDataQueue.poll();
                LogUtil.d(TAG, "addDataToQueue lost packet");
            }
            rtpDataQueue.offer(rtpData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveRtcpData(String remote_ip) {
        System.out.println("收到 【RTCP】 " + " IP:" + remote_ip);
    }

    @Override
    public void receiveBye(String remote_ip) {
        System.out.println("收到 【BYE】 " + " IP:" + remote_ip);
    }

    @Override
    public void setOnRtspEventListener(RTSPClient client, int status) {
        LogUtil.d("OnRtspEvent", "CurrentStatus: " + RtspStatus.getCurrentStatus(status));
        if (client == null) {
            LogUtil.d("OnRtspEvent", "rtspClient == null");
            return;
        }
        switch (status) {
            case RtspStatus.INIT:
                client.doOption();
                break;
            case RtspStatus.OPTIONS:
                client.doDescribe();
                break;
            case RtspStatus.DESCRIBE:
                client.doSetup(localPort);
                break;
            case RtspStatus.SETUP:
                client.doPlay();
                break;
            case RtspStatus.PLAY:

                break;
            case RtspStatus.PAUSE:

                break;
            case RtspStatus.TEARDOWN:

                break;
            case RtspStatus.ERROR:
                fini();
                break;
            case 10:
                //通道被关闭
                if (rtspClient != null) {
                    rtspClient = null;
                }
                break;
            default:
                break;
        }
    }

}
