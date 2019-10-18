package com.wtoe.osd;

public class YuvOsdUtils {

    static {
        System.loadLibrary("yuv-osd");
    }

    /**
     * 初始化时间水印
     *
     * @param osdOffX     水印在视频左上的x偏移
     * @param osdOffY     水印在视频左上的y 偏移
     * @param patternLen  水印格式长度
     * @param frameWidth  相机宽
     * @param frameHeight 相机高
     * @param rotation    旋转角度,0,90,180,270
     */
    public static native long initOsd(int osdOffX, int osdOffY
            , int patternLen, int frameWidth, int frameHeight, int rotation);

    /**
     * 释放内存
     */
    public static native void releaseOsd(long yuvHander);

    public static native void addOsd(long yuvHander ,byte[] yuvInData, byte[] outYvuData, String date);


    /**
     * nv12 与nv21区别
     * NV12: YYYYYYYY UVUV     =>YUV420SP
     * NV21: YYYYYYYY VUVU     =>YUV420SP
     * rgb 转 nv21
     *
     * @param argb
     * @param width
     * @param height
     * @return
     */
    public static native byte[] argbIntToNV21Byte(long yuvHander,int[] argb, int width, int height);

    /**
     * rgb 转nv12
     *
     * @param argb
     * @param width
     * @param height
     * @return
     */
    public static native byte[] argbIntToNV12Byte(long yuvHander,int[] argb, int width, int height);

    /**
     * rgb 转灰度 nv
     * 也就是yuv 中只有 yyyy 没有uv 数据
     *
     * @param argb
     * @param width
     * @param height
     * @return
     */
    public static native byte[] argbIntToGrayNVByte(long yuvHander,int[] argb, int width, int height);

    /**
     * nv21 转 nv 12
     *
     * @param nv21Src  源数据
     * @param nv12Dest 目标数组
     * @param width    数组长度 len=width*height*3/2
     * @param height
     */
    public static native void nv21ToNv12(long yuvHander,byte[] nv21Src, byte[] nv12Dest, int width, int height);

}
