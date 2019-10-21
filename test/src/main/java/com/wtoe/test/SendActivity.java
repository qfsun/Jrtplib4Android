package com.wtoe.test;

import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatEditText;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SendActivity extends AppCompatActivity {

    private CameraHelper helperRtpTestBack;
    private CameraHelper helperRtpTestFront;

    private SurfaceView sv_camera_front;
    private SurfaceView sv_camera_back;
    private AppCompatEditText editText;
    private Button btn;
    private TextView tv_content;

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        initView();
        initListener();
    }

    private void initView() {
        sv_camera_front = findViewById(R.id.sv_camera_front);
        sv_camera_back = findViewById(R.id.sv_camera_back);
        editText = findViewById(R.id.et_dest);
        btn = findViewById(R.id.btn_start);
        tv_content = findViewById(R.id.tv_content);
        sv_camera_front.setKeepScreenOn(true);
        if (sp == null) {
            sp = getPreferences(MODE_PRIVATE);
            String ip = sp.getString("ip", "");
            if (!ip.isEmpty()) {
                editText.setText(ip);
            }
        }
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
                        if (sp == null) {
                            sp = getPreferences(MODE_PRIVATE);
                        }
                        SharedPreferences.Editor edit = sp.edit();
                        edit.putString("ip", s);
                        edit.commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    btn.setText("停止");
                    initData();
                } else if (btn.getText().equals("停止")) {
                    btn.setText("开始");
                    fini();
                }
            }
        });
    }

    private void initData() {
        helperRtpTestFront = new CameraHelper(this, sv_camera_front, Camera.CameraInfo.CAMERA_FACING_FRONT, tv_content);
        SystemClock.sleep(2000);
        helperRtpTestBack = new CameraHelper(this, sv_camera_back, Camera.CameraInfo.CAMERA_FACING_BACK, tv_content);
    }

    private void fini() {
        if (helperRtpTestFront != null) {
            helperRtpTestFront.onDestroy();
            helperRtpTestFront = null;
        }
        if (helperRtpTestBack != null) {
            helperRtpTestBack.onDestroy();
            helperRtpTestBack = null;
        }
    }

    @Override
    protected void onDestroy() {
        fini();
        super.onDestroy();
    }
}
