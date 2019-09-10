#include <RtpCommon.h>

//检测打印错误
bool CheckError(int rtperr) {
    if (rtperr < 0) {
        std::cout << "ERROR: " << RTPGetErrorString(rtperr) << std::endl;
        loge("CheckError!\n");
        return false;
    }
    return true;
}

char *make8Bit(char *p, uint32_t num) {
    //char *res=p;
    char *q = p;
    bool flag = false;
    for (int i = 2; i >= 0; i--) {
        int carry = num / pow(10, i);
        if (carry > 0) {
            *(p++) = carry + '0';
            flag = true;
        } else {
            if (flag) {
                *(p++) = carry + '0';
            }
        }
        num = num % (int) pow(10, i);
    }
    if (!flag) {
        *(p++) = '0';
    }
    *(p++) = '.';
    return p;
}

//C++整数IP转java字符串
char *changeIP(uint32_t num) {
    char *res = new char[16];
    char *p = res;
    for (int i = 0; i < 4; i++) {
        uint32_t tmp = (num >> ((3 - i) * 8)) & 0xFF;
        p = make8Bit(p, tmp);
    }
    p--;
    *p = '\0';
    return res;
}



