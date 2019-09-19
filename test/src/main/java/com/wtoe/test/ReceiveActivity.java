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

public class ReceiveActivity extends AppCompatActivity {

    private static final String TAG = ReceiveActivity.class.getSimpleName();
    private AppCompatEditText editText, et_rtsp;
    private Button btn;
    private TextView tv_content;
    private CheckBox cb_rtsp;

    //是否使用RTSP
    private boolean useRtsp = false;

    private SharedPreferences sp;

    private IpCameraHelper ipCameraHelper;
    private IpCameraHelper ipCameraHelper2;

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
                    if (ipCameraHelper == null) {
                        ipCameraHelper = new IpCameraHelper(NetworkUtil.getInstance().getLocalIp(ReceiveActivity.this), Constants.localPort, Constants.remoteIp, Constants.remotePort);
                    }
                    if (ipCameraHelper2 == null) {
                        ipCameraHelper2 = new IpCameraHelper(NetworkUtil.getInstance().getLocalIp(ReceiveActivity.this), Constants.localPort+4, Constants.remoteIp, Constants.remotePort+2);
                    }
                    //如果勾选了使用rtsp，则初始化rtsp
                    if (useRtsp) {
                        String s = et_rtsp.getText().toString().trim();
                        ipCameraHelper.initRtspClient(s);
                        ipCameraHelper2.initRtspClient(s);
                    }
                    //否则使用默认的接收数据
                    ipCameraHelper.initData();
                    ipCameraHelper2.initData();
                } else if (btn.getText().equals("停止")) {
                    btn.setText("开始");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            fini();
                        }
                    }).start();
                }
            }
        });
    }

    private void fini() {
        if (ipCameraHelper != null) {
            ipCameraHelper.fini();
            ipCameraHelper = null;
        }
        if (ipCameraHelper2 != null) {
            ipCameraHelper2.fini();
            ipCameraHelper2 = null;
        }
    }

    @Override
    protected void onDestroy() {
        fini();
        super.onDestroy();
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
