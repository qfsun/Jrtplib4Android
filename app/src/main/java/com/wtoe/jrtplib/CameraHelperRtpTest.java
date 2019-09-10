package com.wtoe.jrtplib;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import com.wtoe.jrtplib.RtpHandle;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * android相机帮助类
 */
public class CameraHelperRtpTest implements RtpListener {
    private String TAG;

    private MediaCodec mEncoder;

    //视频数据宽
    private int m_width = 1280;

    //视频数据高
    private int m_height = 720;

    //帧率
    private int m_framerate = 25;

    //比特率
    private int m_biterate = 4 * 1024 * 1024;

    private byte[] sps;
    private byte[] pps;

    private Camera mCamera;

    //相机ID
    private int m_CameraId;
    private TextView mTextView;

    private Activity mContext;

    private boolean isRunning;

    private ArrayBlockingQueue<byte[]> nv12Queue = new ArrayBlockingQueue<>(20);

    private byte[] nv12 = new byte[m_width * m_height * 3 / 2];

    private long now;

    private boolean isFirstFrame = true;
    private RtpHandle rtpUtils;

    /**
     * 设置参数
     *
     * @param mCameraId 开启的相机ID(前后置)
     */
    public CameraHelperRtpTest(Activity activity, final SurfaceView surfaceView, int mCameraId, TextView tv_content) {
        this.mContext = activity;
        this.m_CameraId = mCameraId;
        this.mTextView = tv_content;
        this.TAG = CameraHelperRtpTest.class.getSimpleName();
        //得到视频数据回调类
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopPreview();
                destroyCamera();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                createCamera(surfaceView.getHolder());
                initMediaCodec();
                startPreview();
            }
        }).start();
    }

    private ExecutorService encodeExecutor = Executors.newSingleThreadExecutor();

    /**
     * 开启预览
     */
    private synchronized void startPreview() {
        if (mCamera != null && !isRunning) {
            LogUtil.d(TAG, "startPreview");
            isRunning = true;
            initRtpUtils();
            int previewFormat = mCamera.getParameters().getPreviewFormat();
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            int size = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8;
            mCamera.addCallbackBuffer(new byte[size]);
            // Camera  采集信息回调
            // TODO: 17/6/15 获取到数据的格式？ YUV？支持的分辨率？
            mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(final byte[] data, Camera camera) {
                    encodeExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            swapNV21ToNV12(data, nv12, m_width, m_height);
                            nv12Queue.offer(nv12);
                        }
                    });
                    mCamera.addCallbackBuffer(data);
                }
            });
        }
    }

    /**
     * 初始化jrtplib
     */
    private void initRtpUtils() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                rtpUtils = new RtpHandle();
                LogUtil.d(TAG, "initRtpUtils " + Constants.remoteIp + ":" + Constants.remotePort);
                Constants.localPort = rtpUtils.getAvailablePort(Constants.localPort);
                rtpUtils.initSendHandle(Constants.localPort, Constants.remoteIp, Constants.remotePort, CameraHelperRtpTest.this);
            }
        }).start();
    }

    /**
     * 转换色彩空间
     *
     * @param nv21
     * @param nv12
     * @param width
     * @param height
     */
    private void swapNV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    private ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    private MediaCodec.Callback encodeCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            try {
                byte[] data = nv12Queue.poll();
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                inputBuffer.clear();
                if (data != null) {
                    now = System.nanoTime() / 1000;
                    inputBuffer.put(data);
                }
                codec.queueInputBuffer(index, 0, inputBuffer.position(), now, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            try {
                //编码器输出缓冲区
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                byte[] outData = new byte[info.size];
                outputBuffer.get(outData);
                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    findSpsAndPps(outData);
                } else if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    LogUtil.e(TAG, "KEY_FRAME !");
                    //是关键帧，发送sps\pps
                    if (!isFirstFrame) {
                        sendData(sps, true);
                        sendData(pps, true);
                    }
                    isFirstFrame = false;
                }
                if (isFirstFrame) {
                    sendData(sps, true);
                    sendData(pps, true);
                } else {
                    sendData(outData, false);
                }
                codec.releaseOutputBuffer(index, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "Output onError !");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.e(TAG, "onOutputFormatChanged !");
        }
    };

    private void findSpsAndPps(byte[] config) {
        int spsEnd = 1;
        for (int i = 4; i < config.length - 4; i++) {
            if (config[i] == 0X00 && config[i + 1] == 0X00 && config[i + 2] == 0X00 && config[i + 3] == 0X01) {
                spsEnd = i - 1;
                break;
            }
        }
        sps = new byte[spsEnd - 3];
        pps = new byte[config.length - spsEnd - 5];
        System.arraycopy(config, 4, sps, 0, spsEnd - 3);
        System.arraycopy(config, spsEnd + 5, pps, 0, config.length - spsEnd - 5);
    }

    /**
     * 将每帧进行分包并发送数据
     *
     * @param h264Data
     */
    private void sendData(final byte[] h264Data, final boolean isSpsOrPps) {
        if (writeExecutor != null && !writeExecutor.isShutdown()) {
            writeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (rtpUtils != null) {
                        rtpUtils.sendH264Byte(h264Data, h264Data.length, isSpsOrPps);
                    }
                }
            });
        }
    }

    /**
     * 开启摄像头
     *
     * @return
     */
    private boolean createCamera(SurfaceHolder surfaceHolder) {
        try {
            mCamera = Camera.open(m_CameraId);
            Camera.Parameters parameters = mCamera.getParameters();
            //设置预览帧率
            int[] max = determineMaximumSupportedFramerate(parameters);
            parameters.setPreviewFpsRange(max[0], max[1]);
            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(m_CameraId, camInfo);
            int cameraRotationOffset = camInfo.orientation;
            int rotate = (360 + cameraRotationOffset - getDegree(mContext)) % 360;
            parameters.setRotation(rotate);
            parameters.setPreviewFormat(ImageFormat.NV21);
            Camera.Size size = getBestPreviewSize(m_width, m_height, parameters);
            if (size != null) {
                m_width = size.width;
                m_height = size.height;
                //设置预览图像分辨率
                parameters.setPreviewSize(size.width, size.height);
            }
            mCamera.setParameters(parameters);
            int displayRotation;
            displayRotation = (cameraRotationOffset - getDegree(mContext) + 360) % 360;
            mCamera.setDisplayOrientation(displayRotation);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
            return true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            destroyCamera();
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 初始化 MediaCodec 编码器
     */
    private void initMediaCodec() {
        try {
            LogUtil.d(TAG, "initMediaCodec");
            String mimeType = "video/avc";
            int numCodecs = MediaCodecList.getCodecCount();
            MediaCodecInfo codecInfo = null;
            for (int i = 0; i < numCodecs && codecInfo == null; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (!info.isEncoder()) {
                    continue;
                }
                String[] types = info.getSupportedTypes();
                boolean found = false;
                for (int j = 0; j < types.length && !found; j++) {
                    if (types[j].equals(mimeType)) {
                        found = true;
                    }
                }
                if (!found)
                    continue;
                codecInfo = info;
            }
            if (codecInfo == null) {
                return;
            }

            mEncoder = MediaCodec.createEncoderByType("video/avc");
            MediaFormat mediaFormat;
            mediaFormat = MediaFormat.createVideoFormat("video/avc", m_width, m_height);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, m_biterate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);

            mEncoder.setCallback(encodeCallback);
            mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前屏幕旋转角度
     *
     * @return
     */
    private int getDegree(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }

    /**
     * 根据宽高，选择最合适的尺寸
     *
     * @param width
     * @param height
     * @param parameters
     * @return
     */
    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) {
                        result = size;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 计算相机支持的 Framerate
     *
     * @param parameters 相机属性参数
     * @return
     */
    private int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        //获取相机硬件支持的Fps范围参数
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        //遍历获取
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }

    /**
     * 停止预览
     */
    private synchronized void stopPreview() {
        LogUtil.e(TAG, "--stopPreview--\r\n");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    /**
     * 销毁Camera
     */
    private synchronized void destroyCamera() {
        LogUtil.e(TAG, "--destroyCamera--\r\n");
        try {
            if (null != mCamera) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                try {
                    mCamera.release();
                } catch (Exception e) {
                }
                mCamera = null;
            }
            if (encodeExecutor != null) {
                encodeExecutor.shutdown();
            }
            if (writeExecutor != null) {
                writeExecutor.shutdown();
            }
            if (rtpUtils != null) {
                rtpUtils.finiRtp();
                rtpUtils = null;
            }
            isRunning = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放资源
     */
    public void onDestroy() {
        destroyCamera();
        try {
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
                mEncoder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveRtpData(byte[] rtp_data, int pkg_size, boolean isMarker) {
        System.out.println("收到 【RTP】" + Arrays.toString(rtp_data));
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

    private Handler mHandler = new Handler();

    private void addDataToText(final String s) {
        if (mTextView != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    CharSequence last = mTextView.getText().toString().trim();
                    StringBuffer buffer = new StringBuffer(last);
                    buffer.append("\r\n");
                    buffer.append(System.currentTimeMillis());
                    buffer.append(" - ");
                    buffer.append(s);
                    mTextView.setText(buffer.toString());

                    int scrollAmount = mTextView.getLayout().getLineTop(mTextView.getLineCount())
                            - mTextView.getHeight();
                    if (scrollAmount > 0) {
                        mTextView.scrollTo(0, scrollAmount);
                    } else {
                        mTextView.scrollTo(0, 0);
                    }
                }
            });
        }
    }
}
