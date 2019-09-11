package com.wtoe.test;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatEditText;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.wtoe.jrtplib.RtpHandle;
import com.wtoe.test.rtsp.RTSPClient;
import com.wtoe.test.rtsp.RtspEventListener;
import com.wtoe.test.rtsp.RtspStatus;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;

public class ReceiveActivity extends AppCompatActivity implements RtpListener, RtspEventListener {

    private RTSPClient rtspClient;

    private AppCompatEditText editText, et_rtsp;
    private Button btn;
    private TextView tv_content;
    private CheckBox cb_rtsp;

    //是否使用RTSP
    private boolean useRtsp = false;

    private boolean isRunning = true;

    private SharedPreferences sp;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);
        initView();
        initListener();
    }

    private void initView() {
        editText = findViewById(R.id.et_dest);
        btn = findViewById(R.id.btn_start);
        tv_content = findViewById(R.id.tv_content);
        et_rtsp = findViewById(R.id.et_rtsp);
        cb_rtsp = findViewById(R.id.cb_rtsp);
        tv_content.setMovementMethod(new ScrollingMovementMethod());
        tv_content.setKeepScreenOn(true);
        if (sp == null) {
            sp = getPreferences(MODE_PRIVATE);
            String ip = sp.getString("ip", "");
            String rtsp = sp.getString("rtsp", "");
            if (!ip.isEmpty()) {
                editText.setText(ip);
            }
            if (!rtsp.isEmpty()) {
                et_rtsp.setText(rtsp);
            }
        }
        cb_rtsp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                useRtsp = isChecked;
                if (isChecked) {
                    et_rtsp.setVisibility(View.VISIBLE);
                } else {
                    et_rtsp.setVisibility(View.GONE);
                }
            }
        });
    }

    private void initListener() {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btn.getText().equals("开始")) {
                    try {
                        String s = editText.getText().toString().trim();
                        if (!s.isEmpty()) {
                            String tmp[] = s.split(":");
                            Constants.remoteIp = tmp[0];
                            Constants.remotePort = Integer.parseInt(tmp[1]);
                        }
                        String rtsp = et_rtsp.getText().toString().trim();
                        if (sp == null) {
                            sp = getPreferences(MODE_PRIVATE);
                        }
                        SharedPreferences.Editor edit = sp.edit();
                        edit.putString("ip", s);
                        edit.putString("rtsp", rtsp);
                        edit.commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    btn.setText("停止");
                    //如果勾选了使用rtsp，则初始化rtsp
                    if (useRtsp) {
                        String s = et_rtsp.getText().toString().trim();
                        initRtspClient(s);
                    }
                    //否则使用默认的接收数据
                    initData();
                } else if (btn.getText().equals("停止")) {
                    btn.setText("开始");
                    fini();
                }
            }
        });
    }

    private RtpHandle rtpHandle;

    private void initData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                rtpHandle = new RtpHandle();
                String localIp = NetworkUtil.getInstance().getLocalIp(ReceiveActivity.this);
                LogUtil.d("Receive", "localIp:" + localIp + "  / localPort:" + Constants.localPort);
                LogUtil.d("Receive", "remoteIp:" + Constants.remoteIp + "  / remotePort:" + Constants.remotePort);
//                rtpHandle.initReceiveHandle(localIp, Constants.localPort, ReceiveActivity.this);
                Constants.localPort = rtpHandle.getAvailablePort(Constants.localPort);
                rtpHandle.initReceiveAndSendHandle(localIp, Constants.localPort, Constants.remoteIp, Constants.remotePort, ReceiveActivity.this);
                RtpData rtpData;
                while (isRunning) {
                    rtpData = rtpDataQueue.poll();
                    if (rtpData != null && rtpHandle != null) {
                        rtpHandle.sendRtpByte(rtpData.data, rtpData.length, rtpData.isMark);
                    }
                    rtpData = null;
                }
            }
        }).start();

    }

    /**
     * 初始化RTSP客户端调试
     */
    private void initRtspClient(final String rtsp) {
        if (rtsp.isEmpty() || !rtsp.startsWith("rtsp://")) {
            LogUtil.e("initRtspClient", "rtsp地址异常");
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
                            new InetSocketAddress(NetworkUtil.getInstance().getLocalIp(ReceiveActivity.this), 0),
                            rtsp, ReceiveActivity.this);
                    rtspClient.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void fini() {
        isRunning = false;
        if (rtpHandle != null) {
            rtpHandle.finiRtp();
            rtpHandle = null;
        }
        if (rtspClient != null) {
            rtspClient.shutdown();
            rtspClient = null;
        }
    }

    @Override
    protected void onDestroy() {
        fini();
        super.onDestroy();
    }

    int mCount = 0;

    @Override
    public void receiveRtpData(byte[] rtp_data, int pkg_size, boolean isMarker) {
        System.out.println("收到 【RTP】" + pkg_size);
        if (isMarker && mCount % 100 == 0) {
            addDataToText("收到 【RTP】" + pkg_size);
            mCount = 0;
        }
        mCount++;
        addDataToQueue(new RtpData(rtp_data, pkg_size, isMarker));
//        if (rtpHandle != null) {
//            rtpHandle.sendRtpByte(rtp_data, pkg_size, isMarker);
//        }
    }

    private void addDataToQueue(RtpData rtpData) {
        try {
            if (rtpDataQueue.size() >= queueSize) {
                rtpDataQueue.poll();
                LogUtil.d("addDataToQueue","lost packet");
            }
            rtpDataQueue.offer(rtpData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveRtcpData(String remote_ip) {
        System.out.println("收到 【RTCP】 " + " IP:" + remote_ip);
        addDataToText("收到 【RTCP】 " + " IP:" + remote_ip);
    }

    @Override
    public void receiveBye(String remote_ip) {
        System.out.println("收到 【BYE】 " + " IP:" + remote_ip);
        addDataToText("收到 【BYE】 " + " IP:" + remote_ip);
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
                client.doSetup(Constants.localPort);
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

    /**
     * 添加内容至文本输出
     *
     * @param s
     */
    private void addDataToText(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CharSequence last = tv_content.getText().toString().trim();
                StringBuffer buffer = new StringBuffer(last);
                buffer.append("\r\n");
                buffer.append(System.currentTimeMillis());
                buffer.append(" - ");
                buffer.append(s);
                tv_content.setText(buffer.toString());

                int scrollAmount = tv_content.getLayout().getLineTop(tv_content.getLineCount())
                        - tv_content.getHeight();
                if (scrollAmount > 0) {
                    tv_content.scrollTo(0, scrollAmount);
                } else {
                    tv_content.scrollTo(0, 0);
                }
            }
        });
    }
}
