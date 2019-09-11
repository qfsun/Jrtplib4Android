package com.wtoe.test.rtsp;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * author：create by Administrator on 2019/9/5
 * email：1564063766@qq.com
 * remark:
 */
public interface IEvent {
    /**
     * 当channel得到connect事件时调用这个方法
     *
     * @param key
     * @throws IOException
     */
    void connect(SelectionKey key) throws IOException;

    /**
     * 当channel可读时调用这个方法
     *
     * @param key
     * @throws IOException
     */
    void read(SelectionKey key) throws IOException;

    /**
     * 当channel可写时调用这个方法
     *
     * @throws IOException
     */
    void write() throws IOException;

    /**
     * 当channel发生错误时调用
     *
     * @param e
     */
    void error(Exception e);
}
