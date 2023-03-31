//
// Created by Administrator on 2019/9/2.
//
#pragma once
#ifndef JRTPLIB_SENDCALLBACK_H
#define JRTPLIB_SENDCALLBACK_H

#include <jni.h>

class SendCallback {

public:
    jobject jobj;//全局对象
    jmethodID jmid_rtcp;//java 方法id。
    jmethodID jmid_bye;//java 方法id。

public:

    SendCallback(_JNIEnv *env, jobject obj);

    ~SendCallback();
};

#endif //JRTPLIB_SENDCALLBACK_H
