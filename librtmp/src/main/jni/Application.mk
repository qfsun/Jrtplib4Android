#对标准C++ stl库的支持
#APP_STL := gnustl_static stlport_static
APP_STL := c++_static
APP_ABI := armeabi-v7a x86 arm64-v8a
APP_PLATFORM := android-9
APP_CPPFLAGS := -std=c++14 -fexceptions
APP_CFLAGS := -DSTDC_HEADERS -DNO_CRYPTO
