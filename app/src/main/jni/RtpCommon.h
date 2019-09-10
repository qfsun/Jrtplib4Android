//
// Created by Administrator on 2019/9/3.
//
#include <jni.h>
#include <string>
#include <iostream>
#include<android/log.h>
#include <exception>
#include <math.h>

#include "jrtplib3/rtpsession.h"

#ifndef JRTPLIB_RTPCOMMON_H
#define JRTPLIB_RTPCOMMON_H

using namespace jrtplib;

//定义日志宏变量
#define logd(...) __android_log_print(ANDROID_LOG_DEBUG, "wtoe", __VA_ARGS__)
#define loge(...) __android_log_print(ANDROID_LOG_ERROR, "wtoe", __VA_ARGS__)

bool CheckError(int rtperr);
char *changeIP(uint32_t num);

#endif //JRTPLIB_RTPCOMMON_H
