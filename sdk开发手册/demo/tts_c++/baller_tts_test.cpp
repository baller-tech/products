#ifdef _WIN32
#include <windows.h>
#include <conio.h>
#include <locale.h>
#include <io.h>
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
#include <algorithm>

#include "baller_errors.h"
#include "baller_common.h"
#include "baller_tts.h"

// 由北京大牛儿科技发展有限公司统一分配
#define BALLER_ORG_ID                              (0LL)
#define BALLER_APP_ID                              (0LL)
#define BALLER_APP_KEY                             ("")

// SDK参数设置
// 语种
#define BALLER_LANG                                 ("mon")
// 资源文件路径
#define BALLER_RES_DIR                              ("./data/mon")
// 授权文件路径
#define BALLER_LICENSE_FILE                         ("./license/baller_sdk.license")
// 采样率
#define BALLER_SAMPLE_RATE                          (16000)
// 采样点大小
#define BALLER_SAMPLE_SIZE                          (16)
// 音频编码；支持的音频编码请参考开发手册
#define BALLER_AUDIO_ENCODE                         ("raw")

// 测试参数设置
// 测试使用的线程数
#define BALLER_THREAD_COUNT                         (1)
// 每个测试文件执行多少次
#define BALLER_LOOP_COUNT                           (1)
// 测试实时率时打开此宏， 打开此宏时BALLER_AUDIO_ENCODE必须设置为“raw”
// #define BALLER_TEST_RTF

#ifdef _WIN32
#define BALLER_THREAD_ID                            ::GetCurrentThreadId()
#define BALLER_SLEEP_MS(_ms)                        do { Sleep(_ms); } while (false)
#else
#define BALLER_THREAD_ID                            pthread_self()
#define BALLER_SLEEP_MS(_ms)                        do { usleep((_ms) * 1000); } while (false)
#endif

typedef struct t_s_thread_param {
    std::vector<char*> vec_test_data;
    std::vector<int> vec_test_data_len;
    std::vector<std::string> vec_test_file_name;
} s_thread_param;

#ifdef _WIN32
static int GetFiles(const std::string& folder_name, std::vector<std::string>& vec_files)
{
    intptr_t hFile = 0;
    struct _finddata_t fileInfo;
    std::string filter_path_name = folder_name;
    std::string root_path_name = folder_name;
    if (folder_name[folder_name.length() - 1] != '/' && folder_name[folder_name.length() - 1] != '\\')
    {
        filter_path_name += "\\*";
        root_path_name += "\\";
    }
    else
    {
        filter_path_name += "*";
    }

    if ((hFile = _findfirst(filter_path_name.c_str(), &fileInfo)) == -1) {
        return 0;
    }
    do
    {
        if ((fileInfo.attrib & _A_SUBDIR) == 0)
        {
            vec_files.push_back(root_path_name + fileInfo.name);
        }
    } while (_findnext(hFile, &fileInfo) == 0);

    _findclose(hFile);
    return (int)vec_files.size();
}

#else
static int GetFiles(const std::string& folder_name, std::vector<std::string>& vec_files)
{
    DIR *dir;
    struct dirent *ptr;
    if ((dir = opendir(folder_name.c_str())) == 0) {
        return 0;
    }
    while ((ptr = readdir(dir)) != 0) {
        if (strcmp(ptr->d_name, ".") == 0 || strcmp(ptr->d_name, "..") == 0) {
            continue;
        }
        if (ptr->d_type == 8) {
            // read image data
            std::string image_file = folder_name;
            if (folder_name[folder_name.length() - 1] != '/') {
                image_file += '/';
            }
            image_file += ptr->d_name;
            vec_files.push_back(image_file);
        }
    }
    return (int)vec_files.size();
}
#endif // _WIN32

int ReadTestData(const char* pszTestFile, char** ppTestData, int* piTestDataLen)
{
    FILE* pFile = fopen(pszTestFile, "rb");
    if (pFile == 0)
    {
        return 0;
    }
    fseek(pFile, 0, SEEK_END);
    *piTestDataLen = (int)(ftell(pFile));
    if (*piTestDataLen == 0)
    {
        fclose(pFile);
        return 0;
    }

    // 预留结束位置的空字符
    *ppTestData = (char *)malloc((*piTestDataLen) + 1);
    memset(*ppTestData, '\0', (*piTestDataLen) + 1);
    fseek(pFile, 0, SEEK_SET);
    fread(*ppTestData, 1, *piTestDataLen, pFile);
    fclose(pFile);

    return *piTestDataLen;
}


void write_result(FILE* out_file, const char* result, const int result_len)
{
    if (out_file)
    {
        fwrite(result, 1, result_len, out_file);
    }
}

#ifdef _WIN32
DWORD WINAPI test_tts(LPVOID param)
#else
void * test_tts(void * param)
#endif
{
    int ret = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    s_thread_param* thread_param = (s_thread_param *)param;

    // 调用SessionBegin接口
    std::string session_prams = std::string("res_dir=") + BALLER_RES_DIR
        + std::string(",language=") + BALLER_LANG
        + std::string(",sample_rate=") + std::to_string(BALLER_SAMPLE_RATE)
        + std::string(",sample_size=") + std::to_string(BALLER_SAMPLE_SIZE);
    ret = BallerTTSSessionBegin(session_prams.c_str(), &session_id);
    if (ret != BALLER_SUCCESS)
    {
        printf("call BallerTTSSessionBegin failed(%d)\n", ret);
        return 0;
    }
    printf("call BallerTTSSessionBegin success\n");

    int max_elapsed = INT_MIN;
    int min_elapsed = INT_MAX;
    int total_elapsed = 0;
    int total_synthesized_duration = 0;
    int test_count = 0;
    std::string put_param = std::string("input_mode=once,audio_encode=") + BALLER_AUDIO_ENCODE;
    FILE* out_file = 0;
    for (int loop_index = 0; loop_index < BALLER_LOOP_COUNT; ++loop_index)
    {
        for (int file_index = 0; file_index < thread_param->vec_test_file_name.size(); ++file_index)
        {
#ifndef BALLER_TEST_RTF
            if (out_file)
            {
                fclose(out_file);
                out_file = 0;
            }
            std::string out_file_name = thread_param->vec_test_file_name[file_index] + "." + std::to_string(BALLER_THREAD_ID) + "." + BALLER_AUDIO_ENCODE;
            out_file = fopen(out_file_name.c_str(), "wb");
#endif /*BALLER_TEST_RTF*/

            // 调用BallerTTSPut接口
            std::chrono::system_clock::time_point start = std::chrono::system_clock::now();
            ret = BallerTTSPut(session_id, put_param.c_str(), thread_param->vec_test_data[file_index]);
            if (ret != BALLER_SUCCESS)
            {
                printf("call BallerTTSPut failed(%d)\n", ret);
                break;
            }
#ifndef BALLER_TEST_RTF
            printf("call BallerTTSPut success\n");
#endif /*BALLER_TEST_RTF*/

            // 循环调用BallerTTSGet接口
            int synthesized_duration = 0;
            while (true)
            {
                char *result = NULL;
                int result_len = 0;
                ret = BallerTTSGet(session_id, &result, &result_len);
                if (BALLER_MORE_RESULT == ret)
                {
                    synthesized_duration += result_len;
#ifndef BALLER_TEST_RTF
                    write_result(out_file, result, result_len);
                    // 还有识别结果需要获取 需继续调用BallerTTSGet
                    // 为了避免浪费CPU资源停10ms在继续获取，10ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
                    BALLER_SLEEP_MS(10);
#endif /*BALLER_TEST_RTF*/
                    continue;
                }
                else if (BALLER_SUCCESS == ret)
                {
                    synthesized_duration += result_len;
#ifndef BALLER_TEST_RTF
                    write_result(out_file, result, result_len);
                    printf("BallerTTSGet Finish\n");
#endif /*BALLER_TEST_RTF*/
                    break;
                }
                else
                {
                    printf("call BallerTTSGet failed(%d)\n", ret);
                    break;
                }
            }
            if (BALLER_SUCCESS == ret)
            {
                int elapsed = int(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now() - start).count());

                synthesized_duration = synthesized_duration / (BALLER_SAMPLE_SIZE / 8) / (BALLER_SAMPLE_RATE / 1000);
                printf("[%d] loop_index = %d file %s " "synthesized %d ms use %d ms " "rate %f\n",
                    BALLER_THREAD_ID, loop_index, thread_param->vec_test_file_name[file_index].c_str(), 
                    synthesized_duration, elapsed, float(elapsed) / float(synthesized_duration));

                max_elapsed = std::max<int>(max_elapsed, elapsed);
                min_elapsed = std::min<int>(min_elapsed, elapsed);
                total_elapsed += elapsed;
                total_synthesized_duration += synthesized_duration;
                printf("[%d] statistics min use: %d ms max use: %d ms total synthesized %d ms total use %d ms avg rate %f \n",
                    BALLER_THREAD_ID, min_elapsed, max_elapsed, total_synthesized_duration, total_elapsed,
                    float(total_elapsed) / float(total_synthesized_duration));
            }
        }
    }

    if (out_file)
    {
        fclose(out_file);
        out_file = 0;
    }
    ret = BallerTTSSessionEnd(session_id);
    if (ret != BALLER_SUCCESS)
    {
        printf("call BallerTTSSessionEnd failed. Return Code: %d\n", ret);
    }
    printf("call BallerTTSSessionEnd success\n");

    return 0;
}

int main(int argc, char ** argv)
{
    // 检查命令行参数
    if (argc != 3 || (atoi(argv[1]) != 0 && atoi(argv[1]) != 1) || argv[2][0] == 0)
    {
        printf("param 0 : test mode, (0) single file or (1) folder\n");
        printf("param 1 : file name or folder name\n");
        return 0;
    }

    // 读取测试数据
    s_thread_param thread_param;
    if (atoi(argv[1]) == 0)
    {
        thread_param.vec_test_file_name.push_back(argv[2]);
    }
    else
    {
        GetFiles(argv[2], thread_param.vec_test_file_name);
    }
    if (thread_param.vec_test_file_name.size() == 0)
    {
        printf("test file count is 0\n");
        return 0;
    }
    for (int file_index = 0; file_index < (int)thread_param.vec_test_file_name.size(); ++file_index)
    {
        int test_data_len = 0;
        char* test_data = 0;
        ReadTestData(thread_param.vec_test_file_name[file_index].c_str(), &test_data, &test_data_len);
        if (test_data_len == 0 || !test_data)
        {
            printf("test file %s size is 0\n", thread_param.vec_test_file_name[file_index].c_str());

            for (int data_index = 0; data_index < thread_param.vec_test_data.size(); ++data_index)
            {
                if (thread_param.vec_test_data[data_index])
                {
                    free(thread_param.vec_test_data[data_index]);
                }
            }
            thread_param.vec_test_data.clear();
            thread_param.vec_test_data_len.clear();
            thread_param.vec_test_file_name.clear();
            return 0;
        }

        thread_param.vec_test_data.push_back(test_data);
        thread_param.vec_test_data_len.push_back(test_data_len);
    }

    // 参数检查
    if (BALLER_ORG_ID == 0 || BALLER_APP_ID == 0 || std::string(BALLER_APP_KEY).empty())
    {
        printf("please fill in the account information");
        return 0;
    }

    // 调用BallerLogin接口
    std::string login_params = "org_id=" + std::to_string(BALLER_ORG_ID)
        + ",app_id=" + std::to_string(BALLER_APP_ID)
        + ",app_key=" + BALLER_APP_KEY
        + ",license=" + BALLER_LICENSE_FILE
        + ",log_level=warning,log_path=./baller_log";
    int ret = BallerLogin(login_params.c_str());
    if (ret != BALLER_SUCCESS)
    {
        printf("call BallerLogin failed(%d)\n", ret);
        return 0;
    }
    printf("BallerLogin success\n");


    // 启动测试线程
#ifdef _WIN32
    std::vector<HANDLE> thread_handle;
    for (int thread_idx = 0; thread_idx < BALLER_THREAD_COUNT; ++thread_idx) {
        thread_handle.push_back(::CreateThread(0, 0, test_tts, &thread_param, 0, 0));
    }
    ::WaitForMultipleObjects((DWORD)thread_handle.size(), &thread_handle[0], TRUE, INFINITE);
#else
    std::vector<pthread_t> thread_handle;
    for (int thread_idx = 0; thread_idx < BALLER_THREAD_COUNT; ++thread_idx) {
        pthread_t sub_handle;
        pthread_create(&sub_handle, 0, test_tts, &thread_param);
        thread_handle.push_back(sub_handle);
    }
    for (int thread_idx = 0; thread_idx < BALLER_THREAD_COUNT; ++thread_idx) {
        pthread_join(thread_handle[thread_idx], 0);
    }
#endif

    // 释放测试数据占用的内存
    for (int data_index = 0; data_index < thread_param.vec_test_data.size(); ++data_index)
    {
        if (thread_param.vec_test_data[data_index])
        {
            free(thread_param.vec_test_data[data_index]);
        }
    }
    thread_param.vec_test_data.clear();
    thread_param.vec_test_data_len.clear();
    thread_param.vec_test_file_name.clear();

    // 调用BallerLogout接口
    ret = BallerLogout();
    if (ret != BALLER_SUCCESS)
    {
        printf("call BallerLogout failed(%d)\n", ret);
        return 0;
    }
    printf("call BallerLogout success\n");

    return 0;
}