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
#include "baller_od.h"
#include <iostream>
#include <set>
#include <string>

// customer information
#define ORG_ID                            (0LL)
#define APP_ID                            (0LL)
#define APP_KEY                           ("")

#ifdef _WIN32
#define get_thread_id   ::GetCurrentThreadId()
#else  // unix-like
#define get_thread_id   pthread_self()
#endif // _WIN32 / unix-like

typedef struct t_s_thread_param {
    const char * dir;
    char * image_data;
    int image_data_len;
    int loop_cnt;
} s_thread_param;

void BallerSleepMSec(int iMSec)
{
#ifdef _WIN32
    Sleep(iMSec);
#else
    usleep(iMSec * 1000);
#endif
}

int ReadImageData(const char* pszImage, char** ppImageData, int* piImageDataLen)
{
    FILE* pFile = fopen(pszImage, "rb");
    if (pFile == 0)
    {
        return 0;
    }
    fseek(pFile, 0, SEEK_END);
    *piImageDataLen = (int)(ftell(pFile));
    if (*piImageDataLen == 0)
    {
        fclose(pFile);
        return 0;
    }

    *ppImageData = new char[*piImageDataLen];
    memset(*ppImageData, 0, *piImageDataLen);

    fseek(pFile, 0, SEEK_SET);
    fread(*ppImageData, 1, *piImageDataLen, pFile);

    fclose(pFile);
    return *piImageDataLen;
}

#ifdef _WIN32
DWORD WINAPI TestOD(LPVOID param)
#else  // unix-like
void * TestOD(void * param)
#endif // _WIN32 / unix-like
{
    int iRet = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    s_thread_param* thread_param = (s_thread_param *)param;

    // 调用BallerODSessionBegin创建session，一个session同时只能对一张图片进行处理
    std::string session_prams = std::string("res_dir=") + std::string(thread_param->dir);
    iRet = BallerODSessionBegin(session_prams.c_str(), &session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerODSessionBegin failed. Return Code: %d\n", iRet);
        return 0;
    }

    for (int loop_index = 0; loop_index < thread_param->loop_cnt; ++loop_index)
    {
        // 调用BallerODPut开始一张图片的目标检测
        iRet = BallerODPut(session_id, thread_param->image_data, thread_param->image_data_len);
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerODPut failed. Return Code: %d\n", iRet);
            continue;
        }

        // 循环调用BallerODGet获取检测结果
        while (1)
        {
            int iClass = -1;
            int iScore = 0;
            int iX = 0;
            int iY = 0;
            int iWidth = 0;
            int iHeight = 0;
            iRet = BallerODGet(session_id, &iClass, &iScore, &iX, &iY, &iWidth, &iHeight);
            if (BALLER_MORE_RESULT == iRet)
            {
                if (-1 != iClass)
                {
                    printf("class: %d, score: %d, x: %d, y: %d, width: %d, height: %d\n",
                           iClass, iScore, iX, iY, iWidth, iHeight);
                }
                // NOTE: 还有检测结果未获取，需继续调用BallerODGet。
                // 为了避免CPU资源的浪费,这里间隔10ms再继续调用,10ms是经验值，具体间隔多长时间需要根据机器性能和对目标检测的性能要求进行综合考虑。
                // 如果为了测试检测的性能指标，需要注释掉BallerSleepMSec的调用。
                BallerSleepMSec(10);
                continue;
            }
            else if (BALLER_SUCCESS == iRet)
            {
                if (-1 != iClass)
                {
                    printf("class: %d, score: %d, x: %d, y: %d, width: %d, height: %d\n",
                           iClass, iScore, iX, iY, iWidth, iHeight);
                }
                // NOTE: 检测结果获取结束
                printf("finished\n");
                break;
            }
            else
            {
                printf("Call BallerODGet failed. Return Code: %d\n", iRet);
                break;
            }
        }
    }

    // 调用BallerODSessionEnd结束session
    iRet = BallerODSessionEnd(session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerODSessionEnd failed. Return Code: %d\n", iRet);
    }

    return 0;
}

int main(int argc, char ** argv)
{
    if (2 != argc)
    {
        printf("You must specify a file as a command-line argument\n");
        return 0;
    }

    char szVersion[128];
    memset(szVersion, 0, 128);
    BallerODVersion(szVersion, 128);
    std::cout << szVersion << std::endl;

    // 调用BallerLogin接口
    std::string login_params = "org_id=" + std::to_string(ORG_ID) + ","
        + "app_id=" + std::to_string(APP_ID) + ","
        + "app_key=" + APP_KEY + ","
        + "license=license/baller_sdk.license,"
        + "log_level=info,log_path=./baller_log/,";
    int iRet = BallerLogin(login_params.c_str());
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogin failed. Return Code: %d\n", iRet);
        return 0;
    }

    // 读取图片信息
     char* pImageData = 0;
     int iImageDataLen = 0;
     if (0 == ReadImageData(argv[1], &pImageData, &iImageDataLen))
     {
         iRet = BallerLogout();
         if (iRet != BALLER_SUCCESS)
         {
             printf("Call BallerLogout failed. Return Code: %d\n", iRet);
         }
         return 0;
     }

    s_thread_param thread_param;
    // 资源文件存放的路径
    thread_param.dir = "data";
    // 每一个线程循环处理的次数
    thread_param.loop_cnt = 10;
    // 图像信息
     thread_param.image_data = pImageData;
     thread_param.image_data_len = iImageDataLen;
    // 启动的线程数；每一个线程会创建一个session
    const int thread_cnt = 1;

    // 测试目标检测
#ifdef _WIN32
    std::vector<HANDLE> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        thread_handle.push_back(::CreateThread(0, 0, TestOD, &thread_param, 0, 0));
    }
    ::WaitForMultipleObjects((DWORD)thread_handle.size(), &thread_handle[0], TRUE, INFINITE);
#else  // unix-like
    std::vector<pthread_t> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_t sub_handle;
        pthread_create(&sub_handle, 0, TestOD, &thread_param);
        thread_handle.push_back(sub_handle);
    }
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_join(thread_handle[thread_idx], 0);
    }
#endif // _WIN32 / unix-like

    delete[] pImageData;
    pImageData = 0;

    // 调用BallerLogout接口
    iRet = BallerLogout();
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogout failed. Return Code: %d\n", iRet);
        return 0;
    }

    return 0;
}
