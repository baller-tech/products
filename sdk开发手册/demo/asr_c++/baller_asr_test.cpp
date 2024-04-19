#ifdef _WIN32
#include <windows.h>
#endif 

#include <iostream>
#include <chrono>
#include <thread>
#include <string>
#include <sstream>
#include <vector>
#include <fstream>

#include "baller_errors.h"
#include "baller_common.h"
#include "baller_asr.h"


// 由北京市大牛儿科技发展有限公司统一分配
#define ORG_ID                            (0)
#define APP_ID                            (0)
#define APP_KEY                           ("0")

static std::string g_language = "";
static std::string g_pcm_file_name = "";
static int g_loop_count = 1;
static std::vector<char> g_pcm_data;

template <typename T>
static bool ReadFile(const std::string& name, std::vector<T>& buf)
{
    std::ifstream file(name.c_str(), std::ifstream::binary | std::ifstream::ate);
    if (file.is_open())
    {
        buf.resize(file.tellg() / sizeof(T));
        file.seekg(0, file.beg);
        if (buf.size() > 0)
        {
            file.read((char*)&buf[0], buf.size() * sizeof(T));
        }
        return true;
    }

    return false;
}

int DoTask(baller_session_id& session_id)
{
    // 模拟录音设备采集声音，每隔40ms吐出一次40ms的音频
    const int pre_byte_size = 16 * 2 * 40;
    int used_size = 0;
    int ret = BALLER_SUCCESS;

    char *result = nullptr;
    int result_len = 0;
    int curr_status = BALLER_ASR_STATUS_INCOMPLETE;
    unsigned int start_time = 0;
    unsigned int end_time = 0;

    while (g_pcm_data.size() - used_size > pre_byte_size)
    {
        std::stringstream put_param;
        put_param << "dynamic_correction=on" << ",input_mode=continue";
        ret = BallerASRPut(session_id, put_param.str().c_str(), g_pcm_data.data() + used_size, pre_byte_size);
        used_size += pre_byte_size;
        if (BALLER_SUCCESS != ret)
        {
            std::cout << "call BallerASRPut failed(" << ret << ")" << std::endl;
            return ret;
        }

        result = nullptr;
        result_len = 0;
        curr_status = BALLER_ASR_STATUS_INCOMPLETE;
        start_time = 0;
        end_time = 0;
        ret = BallerASRGet(session_id, &result, &result_len, &curr_status, &start_time, &end_time);
        if (BALLER_SUCCESS == ret)
        {
            // 任务计算结束
            if (result && result_len > 0)
            {
                std::cout << "status=" << curr_status << " result=" << result << std::endl;
            }
            return ret;
        }
        else if (BALLER_MORE_RESULT == ret)
        {
            // 还有计算结果没有取出
            if (result && result_len > 0)
            {
                std::cout << "status=" << curr_status << " result=" << result << std::endl;
            }
        }
        else
        {
            // 任务出错
            std::cout << "call BallerASRGet failed(" << ret << ")" << std::endl;
            return ret;
        }
    }

    int left_size = g_pcm_data.size() - used_size;
    std::stringstream put_param;
    put_param << "dynamic_correction=on" << ",input_mode=end";
    ret = BallerASRPut(session_id, put_param.str().c_str(), g_pcm_data.data() + used_size, left_size);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerASRPut failed(" << ret << ")" << std::endl;
        return ret;
    }

    while (true)
    {
        result = nullptr;
        result_len = 0;
        curr_status = BALLER_ASR_STATUS_INCOMPLETE;
        start_time = 0;
        end_time = 0;

        ret = BallerASRGet(session_id, &result, &result_len, &curr_status, &start_time, &end_time);
        if (BALLER_SUCCESS == ret)
        {
            // 任务计算结束 不需要继续调用BallerASRGet
            if (result && result_len > 0)
            {
                std::cout << "status=" << curr_status << " result=" << result << std::endl;
            }
            return ret;
        }
        else if (BALLER_MORE_RESULT == ret)
        {
            // 还有计算结果未取出 需要继续调用BallerASRGet
            if (result && result_len > 0)
            {
                std::cout << "status=" << curr_status << " result=" << result << std::endl;
            }
            // 还有识别结果需要获取 需继续调用BallerASRGet
            // 为了避免浪费CPU资源停5ms在继续获取，5ms为经验值，具体停留的时间需根据机器性能和业务需求综合考虑
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }
        else
        {
            // 任务出错 不需要继续调用BallerASRGet
            std::cout << "call BallerASRGet failed(" << ret << ")" << std::endl;
            return ret;
        }
    }

    return ret;
}

int DoWork()
{
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    std::stringstream session_param;
    session_param << "language=" << g_language
        << ",res_dir=" << "./data/" << g_language
        << ",sample_rate=16000,sample_size=16";
    std::cout << "call BallerASRSessionBegin with param=" << session_param.str() << std::endl;
    int ret = BallerASRSessionBegin(session_param.str().c_str(), &session_id);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerASRSessionBegin failed(" << ret << ")" << std::endl;
        return ret;
    }
    std::cout << "call BallerASRSessionBegin success" << std::endl;
    std::cout << std::endl;

    for (int loop_idx = 0; loop_idx < g_loop_count; ++loop_idx)
    {
        std::cout << "loop_idx=" << loop_idx << std::endl;
        auto begin_pt = std::chrono::steady_clock::now();
        DoTask(session_id);
        int use_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - begin_pt).count();
        std::cout << "loop idx=" << loop_idx << " rate=" << float(use_ms) / float(g_pcm_data.size() / 16 / 2) << std::endl;
        std::cout << std::endl;
    }

    std::cout << "call BallerASRSessionEnd" << std::endl;
    ret = BallerASRSessionEnd(session_id);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerASRSessionEnd failed(" << ret << ")" << std::endl;
        return ret;
    }
    std::cout << "call BallerASRSessionEnd success" << std::endl;
    std::cout << std::endl;
    return BALLER_SUCCESS;
}

int main(int argc, char** argv)
{
#ifdef _WIN32
    system("chcp 65001");
#endif

    if (argc != 3 && argc != 4)
    {
        std::cout << "Usage: " << argv[0] << " language pcm_file_name [loop_count]" << std::endl;
        std::cout << "Example: " <<  argv[0] << " zho zho.pcm 1" << std::endl;
        return 0;
    }
    g_language = argv[1];
    g_pcm_file_name = argv[2];
    if (argc == 4)
    {
        g_loop_count = std::stoi(argv[3]);
    }
    std::cout << "test " << g_language << " " << g_pcm_file_name << std::endl;

    ReadFile(g_pcm_file_name, g_pcm_data);
    if (g_pcm_data.empty())
    {
        std::cout << g_pcm_file_name << " is empty" << std::endl;
        return 0;
    }

    std::stringstream login_param;
    login_param << "org_id=" << ORG_ID << ",app_id=" << APP_ID << ",app_key=" << APP_KEY
        << ",license=license/baller_sdk.license"
        << ",log_level=info, log_path=./baller_log/";
    std::cout << "call BallerLogin with param=" << login_param.str() << std::endl;
    int ret = BallerLogin(login_param.str().c_str());
    if (ret != BALLER_SUCCESS)
    {
        std::cout << "call BallerLogin failed(" << ret << ")" << std::endl;
        return 0;
    }
    std::cout << "call BallerLogin success" << std::endl;
    std::cout << std::endl;

    DoWork();

    std::cout << "call BallerLogout" << std::endl;
    ret = BallerLogout();
    if (ret != BALLER_SUCCESS)
    {
        std::cout << "call BallerLogout failed(" << ret << ")" << std::endl;
        return 0;
    }
    std::cout << "call BallerLogout success" << std::endl;
    std::cout << std::endl;

    return 0;
}