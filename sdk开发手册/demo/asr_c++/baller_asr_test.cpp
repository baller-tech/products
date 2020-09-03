#ifdef _WIN32
#include <windows.h>
#include <conio.h>
#include <locale.h>
#else
#include <pthread.h>
#include <limits.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#endif // _WIN32

#include <stdio.h>
#include <stdlib.h>
#include <chrono>
#include <string>
#include <fstream>
#include <vector>
#include <sstream>
#include <iomanip>
#include <memory>
#include <string.h>

#include "baller_errors.h"
#include "baller_common.h"
#include "baller_asr.h"

/**
 * 根据开发手册或baller_asr.h中的描述，baller asr sdk支持四种常用的使用场景，调用者根据自己的需求确定属于哪一类场景。
 * 1. 音频数据一次性输入且不需要动态修正
 * 2. 音频数据一次性输入且需要动态修正
 * 3. 音频数据连续输入且不需要动态修正
 * 4. 音频数据连续输入且需要动态修正
 * 
 * 本示例中分别显示了四种场景调用的核心代码的逻辑，场景与核心代码逻辑的对应关系为：
 * 1. 音频数据一次性输入且不需要动态修正 请重点参考 test_once_whitout_dynamic_correction 方法
 * 2. 音频数据一次性输入且需要动态修正 请重点参考 test_once_with_dynamic_correction 方法
 * 3. 音频数据连续输入且不需要动态修正 请重点参考 test_continue_whitout_dynamic_correction 方法
 * 4. 音频数据连续输入且需要动态修正  请重点参考 test_continue_with_dynamic_correction 方法
 */

 // 由北京市大牛儿科技发展有限公司统一分配
#define ORG_ID                            (0LL)
#define APP_ID                            (0LL)
#define APP_KEY                           ("")

// 资源文件存放的路径
#define DATA_PATH                       ("data")
// 音频采样格式
#define SAMPLE_RATE                     (16000)
#define SAMPLE_SIZE                     (16)
// 可用语种请参考SDK对应的开发手册
#define LANGUAGE                        ("tib_ad")
// 支持的音频格式请参考SDK对应的开发手册
#define AUDIO_FROMAT                    ("raw")
// 测试音频
#define PCM_FILE                        ("tib_ad.pcm")
// 是否打印子句的偏移信息
#define PRINT_OFFSET                    (0)

#ifdef _WIN32
#define get_thread_id   ::GetCurrentThreadId()
#else  // unix-like
#define get_thread_id   pthread_self()
#endif // _WIN32 / unix-like

typedef struct t_s_thread_param {
    const char * dir;
    const char * language;
    char * pcm_data;
    int pcm_data_len;
    int loop_cnt;

    int sample_rate;
    int sample_size;
} s_thread_param;

typedef struct tag_sentence_info {
    std::string strResult;
    int iStatus;
    unsigned int uStartTime;
    unsigned int uEndTime;
} s_sentence_info;

#ifdef _WIN32
static char *
u8_to_gb(
    const char * u8_str
)
{
    int u16_count = ::MultiByteToWideChar(CP_UTF8, 0, u8_str, -1, 0, 0);
    unsigned short * u16_str = (unsigned short *)malloc(sizeof(unsigned short) * (u16_count + 1));
    memset(u16_str, 0, sizeof(unsigned short) * (u16_count + 1));
    ::MultiByteToWideChar(CP_UTF8, 0, u8_str, -1, (LPWSTR)u16_str, u16_count);

    int gb_count = ::WideCharToMultiByte(CP_ACP, 0, (LPCWSTR)u16_str, -1, 0, 0, 0, 0);
    char * gb_str = (char *)malloc(sizeof(char) * (gb_count + 1));
    memset(gb_str, 0, sizeof(char) * (gb_count + 1));
    ::WideCharToMultiByte(CP_ACP, 0, (LPCWSTR)u16_str, -1, gb_str, gb_count, 0, 0);

    free(u16_str);

    return gb_str;
}
#endif // _WIN32

void BallerSleepMSec(int iMSec)
{
#ifdef _WIN32
    Sleep(iMSec);
#else
    usleep(iMSec * 1000);
#endif
}

int ReadPCMData(const char* pszFile, char** ppPCMData, int* piPCMDataLen)
{
    FILE* pFile = fopen(pszFile, "rb");
    if (pFile == 0)
    {
        return 0;
    }
    fseek(pFile, 0, SEEK_END);
    *piPCMDataLen = (int)(ftell(pFile));
    if (*piPCMDataLen == 0)
    {
        fclose(pFile);
        return 0;
    }

    *ppPCMData = (char *)malloc(*piPCMDataLen);
    memset(*ppPCMData, 0, *piPCMDataLen);

    fseek(pFile, 0, SEEK_SET);
    fread(*ppPCMData, 1, *piPCMDataLen, pFile);

    fclose(pFile);
    return *piPCMDataLen;
}

void show_full_result(const std::vector<s_sentence_info>& vecResult)
{
    for (std::size_t iIndex = 0; iIndex < vecResult.size(); ++iIndex)
    {
#ifdef _WIN32
        char * gb = u8_to_gb(vecResult[iIndex].strResult.c_str());
        printf("%s\n", gb);
        free(gb);
#else
        printf("%s\n", vecResult[iIndex].strResult.c_str());
#endif

#if PRINT_OFFSET
        if (BALLER_ASR_STATUS_COMPLETE  == vecResult[iIndex].iStatus && vecResult[iIndex].uEndTime != 0)
        {
            printf("start: %u ms end: %u\n", vecResult[iIndex].uStartTime, vecResult[iIndex].uEndTime);
        }
#endif /*PRINT_OFFSET*/
    }

    printf("\n");
}

void test_once_whitout_dynamic_correction(baller_session_id session_id, char* pPCMData, int iPCMDataLen)
{
    std::string strOnceParams = std::string("input_mode=once, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
    int iRet = BallerASRPut(session_id, strOnceParams.c_str(), pPCMData, iPCMDataLen);
    if (BALLER_SUCCESS != iRet)
    {
        printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
        return;
    }

    std::vector<s_sentence_info> vecResult;
    while (1)
    {
        char *pResult = NULL;
        int iResultLen = 0;
        int iStatus = 0;
        unsigned int uStartTime = 0;
        unsigned int uEndTime = 0;

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        if (BALLER_SUCCESS == iRet)
        {
            // 当返回值为BALLER_SUCCESS时如果有识别结果，则识别结果的状态一定为BALLER_ASR_STATUS_COMPLETE。
            if (iResultLen > 0 && pResult)
            {
                s_sentence_info sSentenceInfo;
                sSentenceInfo.strResult = std::string(pResult);
                sSentenceInfo.uStartTime = uStartTime;
                sSentenceInfo.uEndTime = uEndTime;
                sSentenceInfo.iStatus = iStatus;
                vecResult.push_back(sSentenceInfo);
                show_full_result(vecResult);
            }
            // 识别结果已获取完毕，不需要继续调用BallerASRGet
            break;
        }
        else if (BALLER_MORE_RESULT == iRet)
        {
            // 不使用动态修正时只需关心子句完整的识别结果；iStatus==BALLER_ASR_STATUS_COMPLETE时的识别结果
            if (iResultLen > 0 && pResult && BALLER_ASR_STATUS_COMPLETE == iStatus)
            {
                s_sentence_info sSentenceInfo;
                sSentenceInfo.strResult = std::string(pResult);
                sSentenceInfo.uStartTime = uStartTime;
                sSentenceInfo.uEndTime = uEndTime;
                sSentenceInfo.iStatus = iStatus;
                vecResult.push_back(sSentenceInfo);
                show_full_result(vecResult);
            }

            // 还有识别结果需要获取 需继续调用BallerASRGet
            // 为了避免浪费CPU资源停10ms在继续获取，10ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
            BallerSleepMSec(10);
            continue;
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            break;
        }
    }
} 

void test_once_with_dynamic_correction(baller_session_id session_id, char* pPCMData, int iPCMDataLen)
{
    std::string strOnceParams = std::string("input_mode=once, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
    int iRet = BallerASRPut(session_id, strOnceParams.c_str(), pPCMData, iPCMDataLen);
    if (BALLER_SUCCESS != iRet)
    {
        printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
        return;
    }

    std::vector<s_sentence_info> vecResult;
    int iLastStatus = BALLER_ASR_STATUS_COMPLETE;

    while (1)
    {
        char *pResult = NULL;
        int iResultLen = 0;
        int iStatus = 0;
        unsigned int uStartTime = 0;
        unsigned int uEndTime = 0;

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        if (BALLER_SUCCESS == iRet)
        {
            if (iResultLen > 0 && pResult)
            {
                if (BALLER_ASR_STATUS_INCOMPLETE == iLastStatus)
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_INCOMPLETE，表示上一次获取的结果是不完整的，本次获取的结果是对上一次获取结果的修正
                    // 这里使用本次获取的结果替换上次获取的结果
                    vecResult.pop_back();
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                else
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_COMPLETE，表示上一次获取的结果是一个子句完整的结果，本次获取的结果是一个新子句的结果
                    // 这里使用本次获取的结果替换上次获取的结果
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                show_full_result(vecResult);
            }

            // 识别结果已获取完毕，不需要继续调用BallerASRGet
            break;
        }
        else if (BALLER_MORE_RESULT == iRet)
        {
            // 使用动态修正时既需关心子句完整的识别结果；也需关心子句中间状态的结果
            if (iResultLen > 0 && pResult)
            {
                if (BALLER_ASR_STATUS_INCOMPLETE == iLastStatus)
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_INCOMPLETE，表示上一次获取的结果是不完整的，本次获取的结果是对上一次获取结果的修正
                    // 这里使用本次获取的结果替换上次获取的结果
                    vecResult.pop_back();
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                else
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_COMPLETE，表示上一次获取的结果是一个子句完整的结果，本次获取的结果是一个新子句的结果
                    // 这里使用本次获取的结果替换上次获取的结果
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }

                iLastStatus = iStatus;
                show_full_result(vecResult);
            }

            // 还有识别结果需要获取 需继续调用BallerASRGet
            // 为了避免浪费CPU资源停10ms在继续获取，10ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
            BallerSleepMSec(10);
            continue;
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            break;
        }
    }
}

void test_continue_whitout_dynamic_correction(baller_session_id session_id, char* pPCMData, int iPCMDataLen)
{
    // 模拟录音设备采集到音频数据后实时的将音频数据发送给sdk；每次向sdk发送40ms的8k16bit的音频数据
    int iPackageSize = 16 * 40;
    int iUsedSize = 0;
    int iRet = BALLER_SUCCESS;
    std::vector<s_sentence_info> vecResult;

    char *pResult = NULL;
    int iResultLen = 0;
    int iStatus = 0;
    unsigned int uStartTime = 0;
    unsigned int uEndTime = 0;

    for (; iPCMDataLen - iUsedSize > iPackageSize; iUsedSize += iPackageSize)
    {
        std::string strContinueParams = std::string("input_mode=continue, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
        iRet = BallerASRPut(session_id, strContinueParams.c_str(), pPCMData + iUsedSize, iPackageSize);
        if (BALLER_SUCCESS != iRet)
        {
            printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
            return;
        }

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        // continue模式下BallerASRPut的input_mode没有传入end时，BallerASRGet不会返回BALLER_SUCCESS。
        if (BALLER_MORE_RESULT == iRet)
        {
            // 不使用动态修正时只需关心子句完整的识别结果；iStatus==BALLER_ASR_STATUS_COMPLETE时的识别结果
            if (iResultLen > 0 && pResult && BALLER_ASR_STATUS_COMPLETE == iStatus)
            {
                s_sentence_info sSentenceInfo;
                sSentenceInfo.strResult = std::string(pResult);
                sSentenceInfo.uStartTime = uStartTime;
                sSentenceInfo.uEndTime = uEndTime;
                sSentenceInfo.iStatus = iStatus;
                vecResult.push_back(sSentenceInfo);
                show_full_result(vecResult);
            }
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            return;
        }
    }

    std::string strEndParams = std::string("input_mode=end, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
    iRet = BallerASRPut(session_id, strEndParams.c_str(), pPCMData + iUsedSize, iPCMDataLen - iUsedSize);
    if (BALLER_SUCCESS != iRet)
    {
        printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
        return;
    }

    while (1)
    {
        char *pResult = NULL;
        int iResultLen = 0;
        int iStatus = 0;
        unsigned int uStartTime = 0;
        unsigned int uEndTime = 0;

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        if (BALLER_SUCCESS == iRet)
        {
            // 当返回值为BALLER_SUCCESS时如果有识别结果，则识别结果的状态一定为BALLER_ASR_STATUS_COMPLETE。
            if (iResultLen > 0 && pResult)
            {
                s_sentence_info sSentenceInfo;
                sSentenceInfo.strResult = std::string(pResult);
                sSentenceInfo.uStartTime = uStartTime;
                sSentenceInfo.uEndTime = uEndTime;
                sSentenceInfo.iStatus = iStatus;
                vecResult.push_back(sSentenceInfo);
                show_full_result(vecResult);
            }

            // 识别结果已获取完毕，不需要继续调用BallerASRGet
            break;
        }
        else if (BALLER_MORE_RESULT == iRet)
        {
            // 不使用动态修正时只需关心子句完整的识别结果；iStatus==BALLER_ASR_STATUS_COMPLETE时的识别结果
            if (iResultLen > 0 && pResult && BALLER_ASR_STATUS_COMPLETE == iStatus)
            {
                s_sentence_info sSentenceInfo;
                sSentenceInfo.strResult = std::string(pResult);
                sSentenceInfo.uStartTime = uStartTime;
                sSentenceInfo.uEndTime = uEndTime;
                sSentenceInfo.iStatus = iStatus;
                vecResult.push_back(sSentenceInfo);
                show_full_result(vecResult);
            }

            // 还有识别结果需要获取 需继续调用BallerASRGet
            // 为了避免浪费CPU资源停10ms在继续获取，10ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
            BallerSleepMSec(10);
            continue;
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            break;
        }
    }
}

void test_continue_with_dynamic_correction(baller_session_id session_id, char* pPCMData, int iPCMDataLen)
{
    // 模拟录音设备采集到音频数据后实时的将音频数据发送给sdk；每次向sdk发送40ms的8k16bit的音频数据
    int iPackageSize = 16 * 40;
    int iUsedSize = 0;
    int iRet = BALLER_SUCCESS;
    int iLastStatus = BALLER_ASR_STATUS_COMPLETE;
    std::vector<s_sentence_info> vecResult;

    char *pResult = NULL;
    int iResultLen = 0;
    int iStatus = 0;
    unsigned int uStartTime = 0;
    unsigned int uEndTime = 0;

    for (; iPCMDataLen - iUsedSize > iPackageSize; iUsedSize += iPackageSize)
    {
        std::string strContinueParams = std::string("input_mode=continue, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
        iRet = BallerASRPut(session_id, strContinueParams.c_str(), pPCMData + iUsedSize, iPackageSize);
        if (BALLER_SUCCESS != iRet)
        {
            printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
            return;
        }

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        // continue模式下BallerASRPut的input_mode没有传入end时，BallerASRGet不会返回BALLER_SUCCESS。
        if (BALLER_MORE_RESULT == iRet)
        {
            if (iResultLen > 0 && pResult)
            {
                if (BALLER_ASR_STATUS_INCOMPLETE == iLastStatus)
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_INCOMPLETE，表示上一次获取的结果是不完整的，本次获取的结果是对上一次获取结果的修正
                    // 这里使用本次获取的结果替换上次获取的结果
                    vecResult.pop_back();
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                else
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_COMPLETE，表示上一次获取的结果是一个子句完整的结果，本次获取的结果是一个新子句的结果
                    // 这里使用本次获取的结果替换上次获取的结果
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }

                iLastStatus = iStatus;
                show_full_result(vecResult);
            }
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            return;
        }
    }

    std::string strEndParams = std::string("input_mode=end, vad=on, audio_format=") + std::string(AUDIO_FROMAT);
    iRet = BallerASRPut(session_id, strEndParams.c_str(), pPCMData + iUsedSize, iPCMDataLen - iUsedSize);
    if (BALLER_SUCCESS != iRet)
    {
        printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
        return;
    }

    while (1)
    {
        char *pResult = NULL;
        int iResultLen = 0;
        int iStatus = 0;
        unsigned int uStartTime = 0;
        unsigned int uEndTime = 0;

        iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus, &uStartTime, &uEndTime);
        if (BALLER_SUCCESS == iRet)
        {
            if (iResultLen > 0 && pResult)
            {

                if (BALLER_ASR_STATUS_INCOMPLETE == iLastStatus)
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_INCOMPLETE，表示上一次获取的结果是不完整的，本次获取的结果是对上一次获取结果的修正
                    // 这里使用本次获取的结果替换上次获取的结果
                    vecResult.pop_back();
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                else
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_COMPLETE，表示上一次获取的结果是一个子句完整的结果，本次获取的结果是一个新子句的结果
                    // 这里使用本次获取的结果替换上次获取的结果
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                show_full_result(vecResult);
            }

            // 识别结果已获取完毕，不需要继续调用BallerASRGet
            break;
        }
        else if (BALLER_MORE_RESULT == iRet)
        {
            if (iResultLen > 0 && pResult)
            {

                if (BALLER_ASR_STATUS_INCOMPLETE == iLastStatus)
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_INCOMPLETE，表示上一次获取的结果是不完整的，本次获取的结果是对上一次获取结果的修正
                    // 这里使用本次获取的结果替换上次获取的结果
                    vecResult.pop_back();
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }
                else
                {
                    // 如果上一次获取结果的状态为BALLER_ASR_STATUS_COMPLETE，表示上一次获取的结果是一个子句完整的结果，本次获取的结果是一个新子句的结果
                    // 这里使用本次获取的结果替换上次获取的结果
                    s_sentence_info sSentenceInfo;
                    sSentenceInfo.strResult = std::string(pResult);
                    sSentenceInfo.uStartTime = uStartTime;
                    sSentenceInfo.uEndTime = uEndTime;
                    sSentenceInfo.iStatus = iStatus;
                    vecResult.push_back(sSentenceInfo);
                }

                iLastStatus = iStatus;
                show_full_result(vecResult);
            }

            // 还有识别结果需要获取 需继续调用BallerASRGet
            // 为了避免浪费CPU资源停10ms在继续获取，10ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
            BallerSleepMSec(10);
            continue;
        }
        else
        {
            // 获取结果出错 不需要继续调用BallerASRGet
            printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
            break;
        }
    }
} 

#ifdef _WIN32
DWORD WINAPI TestASR(LPVOID param)
#else  // unix-like
void * TestASR(void * param)
#endif // _WIN32 / unix-like
{
    int iRet = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    s_thread_param* thread_param = (s_thread_param *)param;

    // Call the BallerASRSessionBegin interface to get session
    std::string session_prams = std::string("res_dir=") + std::string(thread_param->dir) + std::string(",language=") + std::string(LANGUAGE)
        + std::string(",sample_size=") + std::to_string(thread_param->sample_size) + std::string(",sample_rate=") + std::to_string(thread_param->sample_rate)
        + std::string(",engine_type=local,hardware=cpu_slow");

    printf("%s\n", session_prams.c_str());
    iRet = BallerASRSessionBegin(session_prams.c_str(), &session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerASRSessionBegin failed. Return Code: %d\n", iRet);
        return 0;
    }

    for (int loop_index = 0; loop_index < thread_param->loop_cnt; ++loop_index)
    {
        printf("start call test_once_whitout_dynamic_correction\n");
        test_once_whitout_dynamic_correction(session_id, thread_param->pcm_data, thread_param->pcm_data_len);

        printf("\nstart call test_once_with_dynamic_correction\n");
        test_once_with_dynamic_correction(session_id, thread_param->pcm_data, thread_param->pcm_data_len);

        printf("\nstart call test_continue_whitout_dynamic_correction\n");
        test_continue_whitout_dynamic_correction(session_id, thread_param->pcm_data, thread_param->pcm_data_len);

        printf("\nstart call test_continue_with_dynamic_correction\n");
        test_continue_with_dynamic_correction(session_id, thread_param->pcm_data, thread_param->pcm_data_len);
    }

    // Call BallerASRSessionEnd interface to release session
    iRet = BallerASRSessionEnd(session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerASRSessionEnd failed. Return Code: %d\n", iRet);
    }

    return 0;
}

int main()
{
    // Call BallerLogin interface to login
    std::string login_params = "org_id=" + std::to_string(ORG_ID) + ","
        + "app_id=" + std::to_string(APP_ID) + "," + "app_key=" + APP_KEY + ","
        + "license=license/baller_sdk.license,log_level=info,log_path=./baller_log/";
    int iRet = BallerLogin(login_params.c_str());
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogin failed. Return Code: %d\n", iRet);
        return 0;
    }

    // read pcm data
    char* pPCMData = 0;
    int iPCMLen = 0;
    if (0 == ReadPCMData(PCM_FILE, &pPCMData, &iPCMLen))
    {
        printf("read pcm data failed\n");

        iRet = BallerLogout();
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerLogout failed. Return Code: %d\n", iRet);
        }
        return 0;
    }

    s_thread_param thread_param;
    thread_param.dir = "data";
    thread_param.language = LANGUAGE;
    thread_param.loop_cnt = 1;
    thread_param.pcm_data = pPCMData;
    thread_param.pcm_data_len = iPCMLen;
    thread_param.sample_size = SAMPLE_SIZE;
    thread_param.sample_rate = SAMPLE_RATE;
    const int thread_cnt = 1;

#ifdef _WIN32
    std::vector<HANDLE> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        thread_handle.push_back(::CreateThread(0, 0, TestASR, &thread_param, 0, 0));
    }
    ::WaitForMultipleObjects((DWORD)thread_handle.size(), &thread_handle[0], TRUE, INFINITE);
#else  // unix-like
    std::vector<pthread_t> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_t sub_handle;
        pthread_create(&sub_handle, 0, TestASR, &thread_param);
        thread_handle.push_back(sub_handle);
    }
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_join(thread_handle[thread_idx], 0);
    }
#endif // _WIN32 / unix-like

    free(pPCMData);
    pPCMData = 0;

    // Call BallerLogout interface to logout
    iRet = BallerLogout();
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogout failed. Return Code: %d\n", iRet);
        return 0;
    }

    return 0;
}