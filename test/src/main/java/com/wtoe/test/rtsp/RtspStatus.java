package com.wtoe.test.rtsp;

/**
 * author：create by Administrator on 2019/9/6
 * email：1564063766@qq.com
 * remark:与RTSP连接的状态变化
 */
public class RtspStatus {

    //tcp初始化
    public final static int INIT = 0;

    //向RTSP服务发起会话
    public final static int OPTIONS = 1;

    //获取媒体信息
    public final static int DESCRIBE = 2;

    //参数设置
    public final static int SETUP = 3;

    //点播
    public final static int PLAY = 4;

    //暂停
    public final static int PAUSE = 5;

    //关闭
    public final static int TEARDOWN = 6;

    public final static int ERROR = 7;

    /**
     * 获取当前状态值的字符串，方便调试查看
     *
     * @param status 状态值
     * @return 状态值对应的字符串
     */
    public static String getCurrentStatus(int status) {
        String stat;
        switch (status) {
            case INIT:
                stat = "INIT";
                break;
            case OPTIONS:
                stat = "OPTIONS";
                break;
            case DESCRIBE:
                stat = "DESCRIBE";
                break;
            case SETUP:
                stat = "SETUP";
                break;
            case PLAY:
                stat = "PLAY";
                break;
            case PAUSE:
                stat = "PAUSE";
                break;
            case TEARDOWN:
                stat = "TEARDOWN";
                break;
            default:
                stat = "UNKNOWN";
                break;
        }
        return stat;
    }
}
