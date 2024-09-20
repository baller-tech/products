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
#include "baller_nmt.h"

// 由北京市大牛儿科技发展有限公司统一分配
#define ORG_ID                            (0)
#define APP_ID                            (0)
#define APP_KEY                           ("")

// 请填写终端设备的授权
static std::string terminal_sn = u8"";

static std::string g_language = "";
static std::string g_src_file_name = "";
static std::string g_dest_file_name = "";
static int g_loop_count = 1;
static std::vector<std::string> g_src_txt;

static bool ReadSrcTxt(const std::string& name)
{
    std::ifstream file(name.c_str());
    std::string line;
    while (std::getline(file, line))
    {
        g_src_txt.push_back(line);
    }

    return g_src_txt.size() > 0;
}

static int BALLER_CALLBACK my_nmt_callback(void* user_param, const char* txt)
{
    std::ofstream* ofs = (std::ofstream*)user_param;
    *ofs << txt;
    std::cout << "nmt result:" << txt << std::endl;
    return 1;
}

int DoTask(baller_session_id& session_id, const std::string& src_txt, void* user_param)
{
    int ret = BallerNMTPut(session_id, "", src_txt.data(), my_nmt_callback, user_param);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerNMTPut failed(" << ret << ")" << std::endl;
        return ret;
    }

    return ret;
}

int DoWork()
{
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;
    std::stringstream session_param;
    session_param << "language=" << g_language
        << ",res_dir=" << "./nmt-data/" << g_language;
    std::cout << "call BallerNMTSessionBegin with param=" << session_param.str() << std::endl;
    int ret = BallerNMTSessionBegin(session_param.str().c_str(), &session_id);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerNMTSessionBegin failed(" << ret << ")" << std::endl;
        return ret;
    }
    std::cout << "call BallerNMTSessionBegin success" << std::endl;
    std::cout << std::endl;

    int min_use_ms = 999999;
    int max_use_ms = -1;
    int total = 0;

    std::ofstream ofs(g_dest_file_name);
    for (int loop_idx = 0; loop_idx < g_loop_count; ++loop_idx)
    {
        for (int sent_idx = 0; sent_idx < g_src_txt.size(); ++sent_idx)
        {
            std::cout << "loop idx=" << loop_idx << " sent idx=" << sent_idx << std::endl;
            auto begin_pt = std::chrono::steady_clock::now();
            DoTask(session_id, g_src_txt[sent_idx], &ofs);
            int use_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - begin_pt).count();
            min_use_ms = min_use_ms < use_ms ? min_use_ms : use_ms;
            max_use_ms = max_use_ms > use_ms ? max_use_ms : use_ms;
            total += use_ms;
            ofs << std::endl;
            ofs.flush();

            std::cout << "loop idx=" << loop_idx << " sent idx=" << sent_idx << " min=" << min_use_ms << " max=" << max_use_ms << " avg=" << (float(total) / float(loop_idx + 1)) << std::endl;
            std::cout << std::endl;
        }
    }
    ofs.close();

    std::cout << "call BallerNMTSessionEnd" << std::endl;
    ret = BallerNMTSessionEnd(session_id);
    if (BALLER_SUCCESS != ret)
    {
        std::cout << "call BallerNMTSessionEnd failed(" << ret << ")" << std::endl;
        return ret;
    }
    std::cout << "call BallerNMTSessionEnd success" << std::endl;
    std::cout << std::endl;
    return BALLER_SUCCESS;
}

int main(int argc, char** argv)
{
#ifdef _WIN32
    system("chcp 65001");
#endif

    if (terminal_sn.empty())
    {
        std::cout << "请修改terminal_sn变量为实际sn" << std::endl;
        return 0;
    }

    if (argc != 3 && argc != 4)
    {
        std::cout << "Usage: " << argv[0] << " language image_file [loop_count]" << std::endl;
        std::cout << "Example: " << argv[0] << " mon_i-mon_o mon_i.txt 1" << std::endl;
        return 0;
    }
    g_language = argv[1];
    g_src_file_name = argv[2];
    if (argc == 4)
    {
        g_loop_count = std::stoi(argv[3]);
    }
    std::cout << "test " << g_language << " " << g_src_file_name << std::endl;

    if (!ReadSrcTxt(g_src_file_name))
    {
        std::cout << g_src_file_name << " is empty" << std::endl;
        return 0;
    }
    g_dest_file_name = g_src_file_name + ".out.txt";

    std::stringstream login_param;
    login_param << "org_id=" << ORG_ID << ",app_id=" << APP_ID << ",app_key=" << APP_KEY
        << ",license=license/baller_sdk.license"
        << ",log_level=info, log_path=./baller_log/"
        << ",sn=" << terminal_sn;
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
