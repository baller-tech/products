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
#include "baller_ocr.h"

// image file
#define IMAGE_FILE                        ("image.png")
// customer information
#define ORG_ID                            (1)
#define APP_ID                            (11)
#define APP_KEY                           ("xxxx")

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

    *ppImageData = (char *)malloc(*piImageDataLen);
    memset(*ppImageData, 0, *piImageDataLen);

    fseek(pFile, 0, SEEK_SET);
    fread(*ppImageData, 1, *piImageDataLen, pFile);

    fclose(pFile);
    return *piImageDataLen;
}

#ifdef _WIN32
DWORD WINAPI TestOCR(LPVOID param)
#else  // unix-like
void * TestOCR(void * param)
#endif // _WIN32 / unix-like
{
    int iRet = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    s_thread_param* thread_param = (s_thread_param *)param;

    // Call the BallerOCRSessionBegin interface to get session
    std::string session_prams = std::string("res_dir=") + std::string(thread_param->dir) + std::string(",engine_type=local,hardware=cpu_slow,mode=multi_row");
    iRet = BallerOCRSessionBegin(session_prams.c_str(), &session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerOCRSessionBegin failed. Return Code: %d\n", iRet);
        return 0;
    }

    // 调用BallerOCRPut接口传递数据
    int iMax = INT_MIN;
    int iMin = INT_MAX;
    int iTotal = 0;

    for (int loop_index = 0; loop_index < thread_param->loop_cnt; ++loop_index)
    {
        std::chrono::system_clock::time_point start = std::chrono::system_clock::now();

        // Call the BallerOCRPut interface to put image data
        const char* params_once = "input_mode=once";
        iRet = BallerOCRPut(session_id, params_once, thread_param->image_data, thread_param->image_data_len);
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerOCRPut failed. Return Code: %d\n", iRet);

            iRet = BallerOCRSessionEnd(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerOCRSessionEnd failed. Return Code: %d\n", iRet);
            }

            return 0;
        }

        // Call the BallerOCRGet interface to get result
        while (1)
        {
            char *pResult = NULL;
            int iResultLen = 0;

            iRet = BallerOCRGet(session_id, &pResult, &iResultLen);
            if (iRet == BALLER_MORE_RESULT)
            {
                if (iResultLen > 0 && pResult)
                {
                    if (pResult && iResultLen > 0)
                    {
    #ifdef _WIN32
                        char * gb = u8_to_gb((const char *)pResult);
                        printf("%s\n", gb);
                        free(gb);
    #else
                        printf("BallerOCRGet result: %s\n", (char*)pResult);
    #endif
                    }
                }

                // There is also more result, please continue to call the BallerOCRGet interface
                continue;
            }
            else if (iRet == BALLER_SUCCESS)
            {
                if (iResultLen > 0 && pResult)
                {
                    if (pResult && iResultLen > 0)
                    {
    #ifdef _WIN32
                        char * gb = u8_to_gb((const char *)pResult);
                        printf("%s\n", gb);
                        free(gb);
    #else
                        printf("BallerOCRGet result: %s\n", (char*)pResult);
    #endif
                    }
                }
                printf("BallerOCRGet Finish\n");
                break;
            }
            else
            {
                printf("Call BallerOCRGet failed. Return Code: %d\n", iRet);

                break;
            }
        }

        int elapsed = int(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now() - start).count());
        printf("[%d]loop_index = %d use %d ms\n", get_thread_id, loop_index, elapsed);

        if (elapsed > iMax)
        {
            iMax = elapsed;
        }
        if (elapsed < iMin)
        {
            iMin = elapsed;
        }
        iTotal += elapsed;
        printf("[%d] statistics min: %d max: %d avg: %f total: %d loop count: %d\n", get_thread_id, iMin, iMax, float(iTotal) / float(loop_index + 1), iTotal, loop_index + 1);

    }

    // Call BallerOCRSessionEnd interface to release session
    iRet = BallerOCRSessionEnd(session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerOCRSessionEnd failed. Return Code: %d\n", iRet);
    }

    return 0;
}

int main()
{
    // Call BallerLogin interface to login
    std::string login_params = "org_id=" + std::to_string(ORG_ID) + ","
        + "app_id=" + std::to_string(APP_ID) + ","
        + "app_key=" + APP_KEY + ","
        + "license=license/baller_sdk.license,log_level=debug,log_path=./baller_log/";
    int iRet = BallerLogin(login_params.c_str());
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogin failed. Return Code: %d\n", iRet);
        return 0;
    }

    // read image data
    char* pImageData = 0;
    int iImageDataLen = 0;
    if (0 == ReadImageData(IMAGE_FILE, &pImageData, &iImageDataLen))
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
    // 图像文件的信息
    thread_param.image_data = pImageData;
    thread_param.image_data_len = iImageDataLen;
    // 启动的线程数；每一个线程会创建一个session
    const int thread_cnt = 2;

    // 测试图像识别
#ifdef _WIN32
    std::vector<HANDLE> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        thread_handle.push_back(::CreateThread(0, 0, TestOCR, &thread_param, 0, 0));
    }
    ::WaitForMultipleObjects((DWORD)thread_handle.size(), &thread_handle[0], TRUE, INFINITE);
#else  // unix-like
    std::vector<pthread_t> thread_handle;
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_t sub_handle;
        pthread_create(&sub_handle, 0, TestOCR, &thread_param);
        thread_handle.push_back(sub_handle);
    }
    for (int thread_idx = 0; thread_idx < thread_cnt; ++thread_idx) {
        pthread_join(thread_handle[thread_idx], 0);
    }
#endif // _WIN32 / unix-like

    free(pImageData);
    pImageData = 0;

    // Call BallerLogout interface to logout
    iRet = BallerLogout();
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerLogout failed. Return Code: %d\n", iRet);
        return 0;
    }

    return 0;
}