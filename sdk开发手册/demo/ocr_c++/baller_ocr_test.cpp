#ifdef _WIN32
#include <windows.h>
#endif // _WIN32

#include <iostream>
#include <sstream>
#include <fstream>

#include "baller_errors.h"
#include "baller_common.h"
#include "baller_ocr.h"

// 开发者账号信息
#define ORG_ID                            (0)
#define APP_ID                            (0)
#define APP_KEY                           ("")

int ReadImageData(const char* pszImage, char** ppImageData, int* piImageDataLen)
{
    std::ifstream file(pszImage, std::ios::binary | std::ios::ate);
    if (!file.is_open())
    {
        return 0;
    }

    std::streamsize fileSize = file.tellg();
    if (fileSize <= 0)
    {
        file.close();
        return 0;
    }

    *piImageDataLen = static_cast<int>(fileSize);
    *ppImageData = static_cast<char*>(malloc(*piImageDataLen));
    if (*ppImageData == nullptr)
    {
        file.close();
        return 0;
    }
    memset(*ppImageData, 0, *piImageDataLen);

    file.seekg(0, std::ios::beg);
    if (!file.read(*ppImageData, fileSize))
    {
        free(*ppImageData);
        *ppImageData = nullptr;
        *piImageDataLen = 0;
        file.close();
        return 0;
    }

    file.close();
    return *piImageDataLen;
}

void BALLER_CALLBACK my_ocr_cb(void* user_param, const char* content)
{
    if (content)
    {
        printf("%s\n", content);
    }
}

void DoTask(baller_session_id session_id, const std::string& image_path)
{
    char* image_data = nullptr;
    int image_size = 0;
    ReadImageData(image_path.c_str(), &image_data, &image_size);
    if (image_data == nullptr || image_size == 0)
    {
        std::cout << "image file " << image_path << " invalid" << std::endl;
        return;
    }

    int iRet = BallerOCRPut(session_id, "input_mode=once", image_data, image_size, my_ocr_cb, nullptr);
    if (iRet != BALLER_SUCCESS)
    {
        free(image_data);
        std::cout << "BallerOCRPut failed(" << iRet << ")";
        return;
    }

    free(image_data);
}

int main(int argc, char* argv[])
{
#ifdef _WIN32
    system("chcp 65001");
#endif

    if (argc != 4)
    {
        std::cout << "Usage: %s language[mon_i] res_dir[./data/mon_i] image_path[mon_i.jpg]" << std::endl;
        return 0;
    }

    std::string language = argv[1];
    std::string res_dir = argv[2];
    std::string image_path = argv[3];

    std::stringstream login_param;
    login_param << "org_id=" << ORG_ID << ",";
    login_param << "app_id=" << APP_ID << ",";
    login_param << "app_key=" << APP_KEY << ",";
    login_param << "license=license/baller_sdk" << ",";
    login_param << "log_level=debug" << ",";
    login_param << "log_path=./baller_log/" << ",";
    std::cout << "BallerLogin with param: " << login_param.str() << std::endl;
    int iRet = BallerLogin(login_param.str().c_str());
    if (iRet != BALLER_SUCCESS)
    {
        std::cout << "BallerLogin failed(" << iRet << ")" << std::endl;
        return 0;
    }
    std::cout << "BalelrLogin success" << std::endl;

    do 
    {
        std::stringstream session_param;
        session_param << "language=" << language << ",";
        session_param << "res_dir=" << res_dir << ",";
        baller_session_id session_id = BALLER_INVALID_SESSION_ID;
        std::cout << "BallerOCRSessionBegin with param: " << session_param.str() << std::endl;
        iRet = BallerOCRSessionBegin(session_param.str().c_str(), &session_id);
        if (iRet != BALLER_SUCCESS)
        {
            std::cout << "BallerOCRSessionBegin failed(" << iRet << ")" << std::endl;
            break;
        }
        std::cout << "BallerOCRSessionBegin success" << std::endl;


        DoTask(session_id, image_path);

        iRet = BallerOCRSessionEnd(session_id);
        if (iRet != BALLER_SUCCESS)
        {
            std::cout << "BallerOCRSessionEnd failed(" << iRet << ")" << std::endl;
            break;
        }
        std::cout << "BallerOCRSessionEnd success" << std::endl;
    } while (false);

    iRet = BallerLogout();
    if (iRet != BALLER_SUCCESS)
    {
        std::cout << "BallerLogout failed(" << iRet << ")" << std::endl;
        return 0;
    }
    std::cout << "BallerLogout success" << std::endl;

    return 0;
}