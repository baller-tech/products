#include <iostream>
#include <thread>
#include <sstream>
#include <fstream>
#include <vector>

#include "baller_types.h"
#include "baller_common.h"
#include "baller_errors.h"
#include "baller_bi.h"

// 账号信息由大牛儿科技发展有限公司统一分配
const static int64_t kOrgId = 0;
const static int64_t kAppId = 0;
const static std::string kAppKey = "";
// kDomain设置为空即可
const static std::string kDomain = "";

// 用来测试声音输入的文件。
std::string input_file_name_ = "";

int BALLER_CALLBACK my_bi_callback(void* user_param, const int buzz)
{
    std::cout << __FILE__ << " " << __LINE__ << std::endl;
    return 0;
}

void Listen(baller_session_id &session_id)
{
    std::ifstream ifs(input_file_name_, std::ifstream::in | std::ifstream::binary);
    if (!ifs.is_open())
    {
        printf("open file %s failed\n", input_file_name_.c_str());
        return;
    }
    ifs.seekg(0, std::ifstream::end);
    std::size_t file_byte_size = ifs.tellg();
    ifs.seekg(0, std::ios::beg);

    // 每次传入BallerBIPut的音频时长，可以根据业务情况进行调整
    const static int each_duration_ms = 64;
    // 每次传入BallerBIPut的字节数，16k16bit音频每毫秒32个字节
    const static int each_put_byte_size = each_duration_ms * 32;
    std::vector<char> input_data(each_put_byte_size, 0);

    int err_code = BALLER_SUCCESS;
    std::size_t send_finish_byte_size = 0;
    std::stringstream put_param;
    put_param << "input_mode=continue";
    while (file_byte_size - send_finish_byte_size > each_put_byte_size)
    {
        ifs.read(&input_data[0], each_put_byte_size);
        err_code = BallerBIPut(session_id, put_param.str().c_str(),
            input_data.data(), each_put_byte_size, 
            my_bi_callback, nullptr);
        send_finish_byte_size += each_put_byte_size;
        if (BALLER_SUCCESS != err_code)
        {
            printf("put failed: %d\n", err_code);
            break;
        }
    }

    int left_byte_size = file_byte_size - send_finish_byte_size;
    if (BALLER_SUCCESS == err_code && left_byte_size > 0)
    {
        put_param.str("");
        put_param << "input_mode=end";
        ifs.read(&input_data[0], left_byte_size);
        err_code = BallerBIPut(session_id, put_param.str().c_str(),
            input_data.data(), left_byte_size,
            my_bi_callback, nullptr);
        if (BALLER_SUCCESS != err_code)
        {
            printf("put failed: %d\n", err_code);
        }
    }
}

void Check()
{
    baller_session_id session_id;
    std::stringstream session_param;
    session_param << "res_dir=./data";
    session_param << "," << "sample_rate=16000";
    session_param << "," << "sample_size=16";
    session_param << "," << "sound_storage_path=./sound_data/";
    session_param << "," << "sound_storage_size=4294967296";
    printf("session param: %s\n", session_param.str().c_str());
    int err_code = BallerBISessionBegin(session_param.str().c_str(), &session_id);
    if (BALLER_SUCCESS != err_code)
    {
        printf("session begin failed: %d\n", err_code);
        return;
    }
    printf("session begin success\n");

    for (std::size_t loop_idx = 0; loop_idx < 1; ++loop_idx)
    {
        Listen(session_id);
    }

    err_code = BallerBISessionEnd(session_id);
    if (BALLER_SUCCESS != err_code)
    {
        printf("session end failed: %d\n", err_code);
        return;
    }
    printf("session end success\n");
}

int main(int argc, char** argv)
{
    if (2 != argc)
    {
        printf("Usage: %s file_name\n", argv[0]);
        return 0;
    }

    input_file_name_ = argv[1];
    if (input_file_name_.empty())
    {
        printf("input file is empty\n");
        return 0;
    }

    std::stringstream login_param;
    login_param << "org_id=" << kOrgId;
    login_param << "," << "app_id=" << kAppId;
    login_param << "," << "app_key=" << kAppKey;
    login_param << "," << "license=./baller_license/baller_sdk.license";
    login_param << "," << "log_level=info";
    login_param << "," << "log_path=./baller_log/";
    login_param << "," << "log_file_count=2";
    login_param << "," << "log_file_size=2";
    if (!kDomain.empty())
    {
        login_param << "," << "domain=" << kDomain;
    }
    printf("login param: %s\n", login_param.str().c_str());
    int err_code = BallerLogin(login_param.str().c_str());
    if (err_code != BALLER_SUCCESS)
    {
        printf("login failed: %d\n", err_code);
        return 0;
    }
    printf("login success\n");

    Check();

    err_code = BallerLogout();
    if (BALLER_SUCCESS != err_code)
    {
        printf("logout failed  %d\n", err_code);
        return 0;
    }
    printf("login success\n");
}
