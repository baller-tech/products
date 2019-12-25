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

#define DATA_PATH                       ("data")
#define LANGUAGE                        ("tib_kb")
#define SAMPLE_RATE                     (8000)
#define SAMPLE_SIZE                     (16)
// pcm file
#define PCM_FILE                        ("tib_kb.pcm")
// customer information
#define ORG_ID                            (1)
#define APP_ID                            (1)
#define APP_KEY                           ("123")

//#define TEST_ONCE
#define TEST_COUTINUE

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

#ifdef _WIN32
DWORD WINAPI TestASR(LPVOID param)
#else  // unix-like
void * TestASR(void * param)
#endif // _WIN32 / unix-like
{
    int iRet = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    s_thread_param* thread_param = (s_thread_param *)param;

    iRet = BallerASRWorkingThread();
    if (BALLER_SUCCESS != iRet)
    {
        printf("Call BallerASRWorkingThread failed. Return Code: %d\n", iRet);
        return 0;
    }

    // Call the BallerASRSessionBegin interface to get session
    std::string session_prams = std::string("res_dir=") + std::string(thread_param->dir) + std::string(",language=") + std::string(LANGUAGE)
        + std::string(",sample_size=") + std::to_string(thread_param->sample_size) + std::string(",sample_rate=") + std::to_string(thread_param->sample_rate)
        + std::string(",engine_type=local,hardware=cpu_slow,mode=multi_row");

    printf("%s\n", session_prams.c_str());
    iRet = BallerASRSessionBegin(session_prams.c_str(), &session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerASRSessionBegin failed. Return Code: %d\n", iRet);
        return 0;
    }

    for (int loop_index = 0; loop_index < thread_param->loop_cnt; ++loop_index)
    {
#if defined(TEST_ONCE)
        // Call the BallerASRPut interface to put pcm data
        const char* params_once = "input_mode=once";
        iRet = BallerASRPut(session_id, params_once, thread_param->pcm_data, thread_param->pcm_data_len);
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerASRPut failed. Return Code: %d\n", iRet);

            iRet = BallerASRSessionEnd(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerASRSessionEnd failed. Return Code: %d\n", iRet);
            }

            return 0;
        }

        // Call the BallerASRGet interface to get result
        while (1)
        {
            char *pResult = NULL;
            int iResultLen = 0;
            int iStatus = 0;

            iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus);
            if (iRet == BALLER_MORE_RESULT)
            {
                if (iResultLen > 0 && pResult)
                {
#ifdef _WIN32
                    char * gb = u8_to_gb((const char *)pResult);
                    printf("BallerASRGet is complete: %d result: %s\n", iStatus, gb);
                    free(gb);
#else
                    printf("BallerASRGet is complete: %d result: %s\n", iStatus, pResult);
#endif
                }

                // There is also more result, please continue to call the BallerASRGet interface
                continue;
            }
            else if (iRet == BALLER_SUCCESS)
            {
                if (iResultLen > 0 && pResult)
                {
#ifdef _WIN32
                    char * gb = u8_to_gb((const char *)pResult);
                    printf("BallerASRGet is complete: %d result: %s\n", iStatus, gb);
                    free(gb);
#else
                    printf("BallerASRGet is complete: %d result: %s\n", iStatus, pResult);
#endif
                }
                printf("BallerASRGet Finish\n");
                break;
            }
            else
            {
                printf("Call BallerASRGet failed. Return Code: %d\n", iRet);

                break;
            }
        }
#elif defined(TEST_COUTINUE)
        // 模拟录音设备采集到音频数据后实时的将音频数据发送给sdk；每次向sdk发送40ms的8k16bit的音频数据
        int iPackageSize = 16 * 40;
        int iUsedSize = 0;
        for (; thread_param->pcm_data_len - iUsedSize > iPackageSize && (BALLER_SUCCESS == iRet || BALLER_MORE_RESULT == iRet); iUsedSize += iPackageSize)
        {
            const char *pszInputParam = "input_mode=continue";
            iRet = BallerASRPut(session_id, pszInputParam, thread_param->pcm_data + iUsedSize, iPackageSize);
            if (BALLER_SUCCESS == iRet)
            {
                char* pOutData = 0;
                int iOutDataLen = 0;
                int iStatus = 0;
                iRet = BallerASRGet(session_id, &pOutData, &iOutDataLen, &iStatus);

                if (BALLER_SUCCESS == iRet || BALLER_MORE_RESULT == iRet)
                {
                    if (pOutData && iOutDataLen > 0)
                    {
#ifdef _WIN32
                        char * gb = u8_to_gb((const char *)pOutData);
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, gb);
                        free(gb);
#else
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, pOutData);
#endif
                    }
                }
                else
                {
                    printf("Call BallerASRGet failed. Return Code: %d\n", iRet);
                    break;
                }
            }
            else
            {
                printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
                break;
            }
        }

        if (BALLER_SUCCESS == iRet || BALLER_MORE_RESULT == iRet)
        {
            // 处理最后一包
            if (thread_param->pcm_data_len - iUsedSize > 0)
            {
                const char *pszInputParam = "input_mode=end";
                iRet = BallerASRPut(session_id, pszInputParam, thread_param->pcm_data + iUsedSize, thread_param->pcm_data_len - iUsedSize);
                if (BALLER_SUCCESS != iRet)
                {
                    printf("Call BallerASRPut failed. Return Code: %d\n", iRet);
                    break;
                }
            }

            while (1)
            {
                char *pResult = NULL;
                int iResultLen = 0;
                int iStatus = 0;

                iRet = BallerASRGet(session_id, &pResult, &iResultLen, &iStatus);
                if (iRet == BALLER_MORE_RESULT)
                {
                    if (iResultLen > 0 && pResult)
                    {
#ifdef _WIN32
                        char * gb = u8_to_gb((const char *)pResult);
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, gb);
                        free(gb);
#else
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, pResult);
#endif
                    }

                    // There is also more result, please continue to call the BallerASRGet interface
                    continue;
                }
                else if (iRet == BALLER_SUCCESS)
                {
                    if (iResultLen > 0 && pResult)
                    {
#ifdef _WIN32
                        char * gb = u8_to_gb((const char *)pResult);
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, gb);
                        free(gb);
#else
                        printf("BallerASRGet is complete: %d result: %s\n", iStatus, pResult);
#endif
                    }
                    printf("BallerASRGet Finish\n");
                    break;
                }
                else
                {
                    printf("Call BallerASRGet failed. Return Code: %d\n", iRet);

                    break;
                }

                BallerSleepMSec(150);
            }
        }
#endif
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
    thread_param.loop_cnt = 10;
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