//
// Created by Administrator on 2019/9/2.
//
#pragma once
#ifndef JRTPLIB_RECEIVECALLBACK_H
#define JRTPLIB_RECEIVECALLBACK_H

#include <jni.h>

class ReceiveCallback {

public:
    jobject jobj;//全局对象
    jmethodID jmid_rtp;//java 方法id,可以根据实际情况创建多个。
    jmethodID jmid_rtcp;//java 方法id。
    jmethodID jmid_bye;//java 方法id。

public:

    ReceiveCallback(_JNIEnv *env, jobject obj);

    ~ReceiveCallback();
};

#endif //JRTPLIB_RECEIVECALLBACK_H
