#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include <string>

#include "baller_errors.h"
#include "baller_common.h"
#include "baller_vw.h"

// customer information
#define ORG_ID                            (1)
#define APP_ID                            (11)
#define APP_KEY                           ("xxxx")

// pcm file
#define PCM_FILE                        ("DaKaiDianShi.pcm")
// bytes of 200-millisecond audio
#define PASS_BYTE_SIZE_EVERY_TIME       (16 * 2 * 200)

//#define USE_ONCE
#define USE_CONTINUE
#define USE_ABORT

#ifdef USE_ABORT
static int g_isWakeup = 0;
#endif /*USE_ABORT*/

void BallerSleep(int iMSec)
{
#ifdef _WIN32
    Sleep(iMSec);
#else
    usleep(iMSec * 1000);
#endif
}

void BALLER_CALLBACK my_baller_wakeup_cb(void* user_param, const int index)
{
    printf("wake up by word index: %d\n", index);

#ifdef USE_ABORT
    g_isWakeup = 1;
#endif
}

int ReadPCMData(const char* pszPCMFile, char** ppPCMData, int* piPCMDataLen)
{
    FILE* pFile = fopen(pszPCMFile, "rb");
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

void TestPut(char *pPCMData, const int iPCMDataLen)
{
    int iRet = BALLER_SUCCESS;
    baller_session_id session_id = BALLER_INVALID_SESSION_ID;

    // Call the BallerVWSessionBegin interface to get session
    const char* session_prams = "res_dir=./data,wakeup_word=DA KAI DIAN SHI,engine_type=local,hardware=cpu_slow,sample_rate=16,sample_size=16";
    iRet = BallerVWSessionBegin(session_prams, my_baller_wakeup_cb, NULL, &session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerVWSessionBegin failed. Return Code: %d\n", iRet);
        return;
    }

#ifdef USE_ONCE
    // input mode is once
    const char* params_once = "input_mode=once";
    iRet = BallerVWPut(session_id, params_once, pPCMData, iPCMDataLen);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerVWPut failed. Return Code: %d\n", iRet);

        iRet = BallerVWSessionEnd(session_id);
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerVWSessionEnd failed. Return Code: %d\n", iRet);
        }

        return;
    }
#endif /*USE_ONCE*/

#ifdef USE_CONTINUE
    // input mode is continue and end
    g_isWakeup = 0;
    const char* params_continue = "input_mode=continue";
    int iPutIndex = 0;
    while (iPCMDataLen - iPutIndex > PASS_BYTE_SIZE_EVERY_TIME)
    {
        iRet = BallerVWPut(session_id, params_continue, pPCMData + iPutIndex, PASS_BYTE_SIZE_EVERY_TIME);
        printf("put size: %d\n", PASS_BYTE_SIZE_EVERY_TIME);
        iPutIndex += PASS_BYTE_SIZE_EVERY_TIME;

        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerVWPut failed. Return Code: %d\n", iRet);

            iRet = BallerVWSessionEnd(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerVWSessionEnd failed. Return Code: %d\n", iRet);
            }

            return;
        }

#ifdef USE_ABORT
        if (g_isWakeup)
        {
            iRet = BallerVWAbort(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerVWAbort failed. Return Code: %d\n", iRet);
            }

            iRet = BallerVWSessionEnd(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerVWSessionEnd failed. Return Code: %d\n", iRet);
            }

            return;
        }
#endif

        // Mimic the intervals between people speaking
        BallerSleep(200);
    }

    if (iPCMDataLen - iPutIndex > 0)
    {
        const char* params_end = "input_mode=end";
        iRet = BallerVWPut(session_id, params_end, pPCMData + iPutIndex, iPCMDataLen - iPutIndex);
        printf("put size: %d\n", iPCMDataLen - iPutIndex);

        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerVWPut failed. Return Code: %d\n", iRet);

            iRet = BallerVWSessionEnd(session_id);
            if (iRet != BALLER_SUCCESS)
            {
                printf("Call BallerVWSessionEnd failed. Return Code: %d\n", iRet);
            }

            return;
        }
    }
#endif /*USE_CONTINUE*/

    // Call BallerVWSessionEnd interface to release session
    iRet = BallerVWSessionEnd(session_id);
    if (iRet != BALLER_SUCCESS)
    {
        printf("Call BallerVWSessionEnd failed. Return Code: %d\n", iRet);
    }
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

    // read pcm data
    char* pPCMData = 0;
    int iPCMDataLen = 0;
    if (0 == ReadPCMData(PCM_FILE, &pPCMData, &iPCMDataLen))
    {
        iRet = BallerLogout();
        if (iRet != BALLER_SUCCESS)
        {
            printf("Call BallerLogout failed. Return Code: %d\n", iRet);
        }

        printf("Read PCM file (%s) failed.\n", PCM_FILE);
        return 0;
    }

    // run vw
    TestPut(pPCMData, iPCMDataLen);

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