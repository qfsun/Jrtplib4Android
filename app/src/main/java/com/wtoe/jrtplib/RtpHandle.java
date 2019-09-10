package com.wtoe.jrtplib;

import java.io.IOException;
import java.net.ServerSocket;

public class RtpHandle {
    static {
        System.loadLibrary("rtp-handle");
    }

    /**
     * 生成一个可使用的udp端口（20000以上）
     *
     * @param port 传20000即可
     * @return
     */
    public static int getAvailablePort(int port) {
        try {
            ServerSocket socket = new ServerSocket(port);
            socket.close();
            return port;
        } catch (IOException e) {
            return getAvailablePort(port + 2);
        }
    }

    /**
     * 只发送数据，不做接收处理
     */
    public static native boolean initSendHandle(int localport, String desthost, int destport, RtpListener listener);

    /**
     * 只接收数据，不做发送处理
     */
    public static native boolean initReceiveHandle(String localhost, int localport, RtpListener listener);

    /**
     * 接收数据，并转发
     */
    public static native boolean initReceiveAndSendHandle(String localhost, int localport, String desthost, int destport, RtpListener listener);

    /**
     * 发送数据
     */
    public static native boolean sendH264Byte(byte[] src, int byteLength, boolean isSpsOrPps);

    /**
     * 发送数据
     */
    public static native boolean sendRtpByte(byte[] src, int byteLength, boolean isMarker);

    /**
     * 销毁资源
     */
    public static native boolean finiRtp();

}
