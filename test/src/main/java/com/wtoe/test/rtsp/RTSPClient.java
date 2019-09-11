package com.wtoe.test.rtsp;

import com.wtoe.test.LogUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * author：create by Administrator on 2019/9/5
 * email：1564063766@qq.com
 * remark:RTSP客户端功能实现
 */
public class RTSPClient extends Thread implements IEvent {
    private static final String VERSION = " RTSP/1.0\r\n";
    private static final String RTSP_OK = "RTSP/1.0 200 OK";
    private static final String TAG = RTSPClient.class.getSimpleName();

    //监听回调
    private RtspEventListener rtspListener;

    //远程地址
    private final InetSocketAddress remoteAddress;

    //本地地址
    private final InetSocketAddress localAddress;

    //连接通道
    private SocketChannel socketChannel;

    // 发送缓冲区
    private final ByteBuffer sendBuf;

    //接收缓冲区
    private final ByteBuffer receiveBuf;

    private static final int BUFFER_SIZE = 8192;

    //端口选择器
    private Selector selector;

    private String address;

    private String sessionid;

    private int sysStatus;

    // 线程是否结束的标志
    private AtomicBoolean shutdown;

    private int seq = 1;

    private boolean isSended;

    private String trackInfo;

    public RTSPClient(InetSocketAddress remoteAddress,
                      InetSocketAddress localAddress, String address, RtspEventListener listener) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.address = address;
        this.rtspListener = listener;

        // 初始化缓冲区
        sendBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        receiveBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        if (selector == null) {
            // 创建新的Selector
            try {
                selector = Selector.open();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        startup();
        shutdown = new AtomicBoolean(false);
        isSended = false;
        sysStatus = RtspStatus.INIT;
    }

    /**
     * 开启TCP建链
     */
    private void startup() {
        try {
            // 打开通道
            socketChannel = SocketChannel.open();
            // 绑定到本地端口
            socketChannel.socket().setSoTimeout(30000);
            socketChannel.configureBlocking(false);
            socketChannel.socket().bind(localAddress);
            if (socketChannel.connect(remoteAddress)) {
                System.out.println("开始建立连接:" + remoteAddress);
            }
            socketChannel.register(selector, SelectionKey.OP_CONNECT
                    | SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
            System.out.println("端口打开成功");
        } catch (final IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * 发送数据
     *
     * @param out
     */
    private void send(byte[] out) {
        if (out == null || out.length < 1) {
            return;
        }
        synchronized (sendBuf) {
            sendBuf.clear();
            sendBuf.put(out);
            sendBuf.flip();
        }
        // 发送出去
        try {
            write();
            isSended = true;
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向通道写入数据
     *
     * @throws IOException
     */
    public void write() throws IOException {
        if (isConnected()) {
            try {
                socketChannel.write(sendBuf);
            } catch (final IOException e) {
            }
        } else {
            System.out.println("通道为空或者没有连接上");
        }
    }

    /**
     * 接收TCP数据
     *
     * @return
     */
    private byte[] recieve() {
        if (isConnected()) {
            try {
                int len = 0;
                int readBytes = 0;

                synchronized (receiveBuf) {
                    receiveBuf.clear();
                    try {
                        while ((len = socketChannel.read(receiveBuf)) > 0) {
                            readBytes += len;
                        }
                    } finally {
                        receiveBuf.flip();
                    }
                    if (readBytes > 0) {
                        final byte[] tmp = new byte[readBytes];
                        receiveBuf.get(tmp);
                        return tmp;
                    } else {
                        System.out.println("接收到数据为空,重新启动连接");
                        return null;
                    }
                }
            } catch (final IOException e) {
                System.out.println("接收消息错误:");
            }
        } else {
            System.out.println("端口没有连接");
        }
        return null;
    }

    private boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    private void select() {
        int n = 0;
        try {
            if (selector == null) {
                return;
            }
            n = selector.select(1000);
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // 如果select返回大于0，处理事件
        if (n > 0) {
            for (final Iterator<SelectionKey> i = selector.selectedKeys()
                    .iterator(); i.hasNext(); ) {
                // 得到下一个Key
                final SelectionKey sk = i.next();
                i.remove();
                // 检查其是否还有效
                if (!sk.isValid()) {
                    continue;
                }
                // 处理事件
                final IEvent handler = (IEvent) sk.attachment();
                try {
                    if (sk.isConnectable()) {
                        handler.connect(sk);
                    } else if (sk.isReadable()) {
                        handler.read(sk);
                    } else {
                        // System.err.println("Ooops");
                    }
                } catch (final Exception e) {
                    handler.error(e);
                    sk.cancel();
                }
            }
        }
    }

    public void shutdown() {
        if (isConnected()) {
            try {
                socketChannel.close();
                System.out.println("端口关闭成功");
            } catch (final IOException e) {
                System.out.println("端口关闭错误:");
            } finally {
                socketChannel = null;
                if (rtspListener != null){
                    rtspListener.setOnRtspEventListener(this,10);
                }
            }
        } else {
            System.out.println("通道为空或者没有连接");
        }
    }

    @Override
    public void run() {
        // 启动主循环流程
        while (!shutdown.get()) {
            try {
                if (sysStatus == RtspStatus.INIT) {
                    if (rtspListener != null) {
                        rtspListener.setOnRtspEventListener(this, sysStatus);
                    }
                }
                // do select
                select();
                try {
                    Thread.sleep(1000);
                } catch (final Exception e) {
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        shutdown();
    }

    @Override
    public void connect(SelectionKey key) throws IOException {
        if (isConnected()) {
            return;
        }
        // 完成SocketChannel的连接
        socketChannel.finishConnect();
        while (!socketChannel.isConnected()) {
            try {
                Thread.sleep(300);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            socketChannel.finishConnect();
        }

    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
    }

    @Override
    public void read(SelectionKey key) throws IOException {
        // 接收消息
        final byte[] msg = recieve();
        if (msg != null) {
            handle(msg);
        } else {
            key.cancel();
        }
    }

    /**
     * 处理消息
     *
     * @param msg
     */
    private void handle(byte[] msg) {
        String tmp = new String(msg);
        LogUtil.d(TAG, "返回内容：\r\n" + tmp);
        if (tmp.startsWith(RTSP_OK)) {
            isSended = false;
            switch (sysStatus) {
                case RtspStatus.INIT:
                    trackInfo = null;
                    sessionid = null;
                case RtspStatus.PLAY:
                case RtspStatus.OPTIONS:
                case RtspStatus.TEARDOWN:
                    if (rtspListener != null) {
                        rtspListener.setOnRtspEventListener(this,sysStatus);
                    }
                    break;
                case RtspStatus.DESCRIBE:
                    obtainKeyData(tmp);
                    if (trackInfo != null && trackInfo.length() > 0) {
                        if (rtspListener != null) {
                            rtspListener.setOnRtspEventListener(this,sysStatus);
                        }
                    }
                    break;
                case RtspStatus.SETUP:
                    obtainKeyData(tmp);
                    if (sessionid != null && sessionid.length() > 0) {
                        if (rtspListener != null) {
                            rtspListener.setOnRtspEventListener(this,sysStatus);
                        }
                    }
                    break;
                case RtspStatus.PAUSE:
                    shutdown.set(true);
                    if (rtspListener != null) {
                        rtspListener.setOnRtspEventListener(this,sysStatus);
                    }
                    break;
                default:
                    break;
            }

        } else {
            System.out.println("返回错误：" + tmp);
        }
    }

    /**
     * 获取重要的数据
     *
     * @param tmp
     */
    private void obtainKeyData(String tmp) {
        StringReader reader = new StringReader(tmp);
        BufferedReader bufferedReader = new BufferedReader(reader);
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null)
                if (!line.isEmpty()) {
                    if (line.contains("a=control:trackID")) {
                        trackInfo = line.substring(line.indexOf(":") + 1).trim();
                    } else if (line.contains("Session")) {
                        sessionid = line.substring(line.indexOf(":") + 1).trim();
                    }
                }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭
     */
    public void doTeardown() {
        if (isConnected() && (!isSended)) {
            if (sysStatus == RtspStatus.INIT || sysStatus == RtspStatus.TEARDOWN) {
                LogUtil.d(TAG, " doTeardown Error, Current Status :" + RtspStatus.getCurrentStatus(sysStatus));
                return;
            }
            sysStatus = RtspStatus.TEARDOWN;
            System.out.println("==================doTeardown==================");
            StringBuilder sb = new StringBuilder();
            sb.append("TEARDOWN ");
            sb.append(this.address);
            sb.append("/");
            sb.append(VERSION);
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0 \r\n");
            sb.append("Session: ");
            sb.append(sessionid);
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }

    /**
     * 点播
     */
    public void doPlay() {
        if (isConnected() && (!isSended)) {
            if (sysStatus != RtspStatus.SETUP || sessionid == null || sessionid.isEmpty()) {
                LogUtil.d(TAG, " doPlay Error, Current Status :" + RtspStatus.getCurrentStatus(sysStatus));
                return;
            }
            sysStatus = RtspStatus.PLAY;
            System.out.println("==================doPlay==================");
            StringBuilder sb = new StringBuilder();
            sb.append("PLAY ");
            sb.append(this.address);
            sb.append(VERSION);
            sb.append("Session: ");
            sb.append(sessionid);
            sb.append("\r\n");
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0");
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }

    /**
     * 设置参数
     */
    public void doSetup(int localPort) {
        if (isConnected() && (!isSended)) {
            if (sysStatus != RtspStatus.DESCRIBE) {
                LogUtil.d(TAG, " doSetup Error, Current Status :" + RtspStatus.getCurrentStatus(sysStatus));
                return;
            }
            sysStatus = RtspStatus.SETUP;
            System.out.println("==================doSetup==================");
            StringBuilder sb = new StringBuilder();
            sb.append("SETUP ");
            sb.append(this.address);
            sb.append("/");
            sb.append(trackInfo);
            sb.append(VERSION);
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            //RTCP需要使用奇数端口，RTP使用偶数端口
            if (localPort % 2 == 0) {
                sb.append("Transport: RTP/AVP;UNICAST;client_port=" + localPort + "-" + (localPort + 1) + ";mode=play");
            } else {
                sb.append("Transport: RTP/AVP;UNICAST;client_port=" + (localPort - 1) + "-" + localPort + ";mode=play");
            }
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0");
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }

    /**
     * 会话开始
     */
    public void doOption() {
        if (isConnected() && (!isSended)) {
            sysStatus = RtspStatus.OPTIONS;
            System.out.println("==================doOption==================");
            StringBuilder sb = new StringBuilder();
            sb.append("OPTIONS ");
            sb.append(this.address.substring(0, address.lastIndexOf("/")));
            sb.append(VERSION);
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0");
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }

    /**
     * 获取媒体信息
     */
    public void doDescribe() {
        if (isConnected() && (!isSended)) {
            sysStatus = RtspStatus.DESCRIBE;
            System.out.println("==================doDescribe==================");
            StringBuilder sb = new StringBuilder();
            sb.append("DESCRIBE ");
            sb.append(this.address);
            sb.append(VERSION);
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0");
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }

    /**
     * 暂停
     */
    public void doPause() {
        if (isConnected() && (!isSended)) {
            if (sysStatus != RtspStatus.PLAY) {
                LogUtil.d(TAG, " doPause Error, Current Status :" + RtspStatus.getCurrentStatus(sysStatus));
                return;
            }
            sysStatus = RtspStatus.PAUSE;
            System.out.println("=================doPause===================");
            StringBuilder sb = new StringBuilder();
            sb.append("PAUSE ");
            sb.append(this.address);
            sb.append("/");
            sb.append(VERSION);
            sb.append("CSeq: ");
            sb.append(seq++);
            sb.append("\r\n");
            sb.append("Session: ");
            sb.append(sessionid);
            sb.append("\r\n");
            sb.append("User-Agent: Wtoe Android /1.0.0");
            sb.append("\r\n");
            send(sb.toString().getBytes());
            LogUtil.d(TAG, sb.toString());
        } else {
            LogUtil.d(TAG, " do Error, TCP unConnect or is sending.");
        }
    }
}
