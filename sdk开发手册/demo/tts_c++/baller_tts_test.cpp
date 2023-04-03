#include <iostream>
#include <sstream>
#include <thread>
#include <chrono>
#include <fstream>
#include <vector>
#include <string.h>
#include <chrono>
#include <limits.h>

#include "baller_common.h"
#include "baller_errors.h"
#include "baller_types.h"
#include "baller_tts.h"

/**
* pcm 转 wav 方便测试播放
*/
typedef struct {
    char            riffType[4];        //4byte,资源交换文件标志:RIFF   
    unsigned int    riffSize;           //4byte,从下个地址到文件结尾的总字节数  
    char            wavType[4];         //4byte,wav文件标志:WAVE    
    char            formatType[4];      //4byte,波形文件标志:FMT(最后一位空格符)    
    unsigned int    formatSize;         //4byte,音频属性(compressionCode,numChannels,sampleRate,bytesPerSecond,blockAlign,bitsPerSample)所占字节数
    unsigned short  compressionCode;    //2byte,格式种类(1-线性pcm-WAVE_FORMAT_PCM,WAVEFORMAT_ADPCM)
    unsigned short  numChannels;        //2byte,通道数
    unsigned int    sampleRate;         //4byte,采样率
    unsigned int    bytesPerSecond;     //4byte,传输速率
    unsigned short  blockAlign;         //2byte,数据块的对齐，即DATA数据块长度
    unsigned short  bitsPerSample;      //2byte,采样精度-PCM位宽
    char            dataType[4];        //4byte,数据标志:data
    unsigned int    dataSize;           //4byte,从下个地址到文件结尾的总字节数，即除了wav header以外的pcm data length
} wav_head_data_t;

static bool SaveWavFile(const std::string& file_name, const std::vector<char>& pcm_data)
{
    int pcm_byte_size = pcm_data.size();

    // config wav head
    wav_head_data_t wav_head;
    memcpy(wav_head.riffType, "RIFF", strlen("RIFF"));
    memcpy(wav_head.wavType, "WAVE", strlen("WAVE"));
    wav_head.riffSize = 36 + pcm_byte_size;
    wav_head.sampleRate = 16000;
    wav_head.bitsPerSample = 16;
    memcpy(wav_head.formatType, "fmt ", strlen("fmt "));
    wav_head.formatSize = 16;
    wav_head.numChannels = 1;
    wav_head.blockAlign = wav_head.numChannels * wav_head.bitsPerSample / 8;
    wav_head.compressionCode = 1;
    wav_head.bytesPerSecond = wav_head.sampleRate * wav_head.blockAlign;
    memcpy(wav_head.dataType, "data", strlen("data"));
    wav_head.dataSize = pcm_byte_size;

    // write wave
    std::ofstream file_wav(file_name.c_str(), std::ofstream::binary);
    if (!file_wav.is_open())
    {
        std::cout << "save wave file " << file_name.c_str() << " failed" << std::endl;
        return false;
    }

    file_wav.write((const char *)&wav_head, sizeof(wav_head));
    if (pcm_data.size() > 0)
    {
        file_wav.write((const char *)&pcm_data[0], pcm_byte_size);
    }

    return true;
}

const static int64_t kOrgId = 0LL;
const static int64_t kAppId = 0LL;
const static std::string kAppKey = "";

static std::string language;
static std::string out_dir;
static std::string text_file_name;
static float speed = 1.0f;
static std::string ability_server;

struct BallerCallbackParam {
    std::vector<char> pcm_data;
    std::chrono::steady_clock::time_point begin_pt;
    int first_elapsed;
    int first_byte_size;

    std::chrono::steady_clock::time_point first_frame_finish_pt;
    int frame_idx;
    int pcm_size;

    std::chrono::steady_clock::time_point last_pt;
};

int BALLER_CALLBACK my_tts_callback(void* user_param, const void * wav, const int size)
{
    BallerCallbackParam* callback_param = (BallerCallbackParam*)(user_param);
    callback_param->frame_idx += 1;
    if (1 == callback_param->frame_idx)
    {
        callback_param->first_elapsed = int(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - callback_param->begin_pt).count());
        callback_param->first_byte_size = size;

        callback_param->pcm_size = size;
        callback_param->first_frame_finish_pt = std::chrono::steady_clock::now();
    }
    else
    {   
        int temp_slow_elapsed = int(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - callback_param->first_frame_finish_pt).count()) - (callback_param->pcm_size / 32);
        if (temp_slow_elapsed > 0)
        {
            printf("frame %d is slow %d ms\n", callback_param->frame_idx, temp_slow_elapsed);
            callback_param->pcm_size = size;
            callback_param->first_frame_finish_pt = std::chrono::steady_clock::now();
        }
        else
        {
            callback_param->pcm_size += size;            
        }
    }

    callback_param->pcm_data.insert(callback_param->pcm_data.end(), (char*)wav, (char*)wav + size);
    callback_param->last_pt = std::chrono::steady_clock::now();
    return 1;
}

int DoTTS()
{
    char version[256] = { 0 };
    BallerTTSVersion(version, 255);
    std::cout << version << std::endl; 

    baller_session_id session_id = BALLER_INVALID_SESSION_ID;

    std::stringstream ss;
    ss << "res_dir=./data/" << language;
    ss << ",language=" << language;
    if (!ability_server.empty())
    {
        ss << ",engine_type=cloud";
    }
    std::cout << "sesion param: " << ss.str().c_str() << std::endl;
    int code = BallerTTSSessionBegin(ss.str().c_str(), &session_id);
    if (BALLER_SUCCESS != code)
    {
        std::cout << "session begin failed " << code << std::endl;
        return code;
    }
    std::cout << "session begin success" << std::endl;

    std::ifstream file_in(text_file_name);
    if (!file_in.is_open())
    {
        std::cout << "打开文件" << text_file_name.c_str() << "失败，请检查文件是否存在。" << std::endl;
        return -1;
    }

    int sentence_index = 0;
    int total_elapsed = 0;
    int total_wav_size = 0;
    int total_first_elapsed = 0;
    int total_first_point_count = 0;
    char wav_file_name[64] = { 0 };

    std::string txt;
    while(getline(file_in, txt))
    {
        std::string put_param = "speed=" + std::to_string(speed);
        sentence_index += 1;

        BallerCallbackParam callback_param;
        callback_param.first_elapsed = 0;
        callback_param.first_byte_size = 0;
        callback_param.frame_idx = 0;
        callback_param.pcm_size = 0;
        callback_param.begin_pt = std::chrono::steady_clock::now();

        code = BallerTTSPut(session_id, put_param.c_str(), txt.c_str(), my_tts_callback, &callback_param);
        int elapsed = int(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - callback_param.begin_pt).count());
        if (BALLER_SUCCESS != code)
        {
            std::cout << "session put failed " << code << std::endl;
            break;
        }

        int wav_point_count = callback_param.pcm_data.size() / 2;
        int first_point_count = callback_param.first_byte_size / 2;

        total_elapsed += elapsed;
        total_wav_size += wav_point_count;

        total_first_elapsed += callback_param.first_elapsed;
        total_first_point_count += first_point_count;

        float duration = float(wav_point_count) / float(16);
        std::cout << "sentence: " << sentence_index << " voice duration: " << duration << "ms synthesis elapsed: " << elapsed << "ms rate: " << float(elapsed) / float(duration) << std::endl;
        duration = float(first_point_count) / float(16);
        std::cout << "sentence: " << sentence_index << " first pack duration: " << duration << "ms first pack synthesis elapsed: " << callback_param.first_elapsed << "ms" << std::endl;

        std::cout << "[+] " << " first avg synthesis elapsed: " << float(total_first_elapsed) / float(sentence_index) << "ms";
        std::cout << " total rate: " << float(total_elapsed) / (float(total_wav_size) / float(16));
        std::cout << std::endl;

        sprintf(wav_file_name, "%05d.wav", sentence_index);
        if (!SaveWavFile(out_dir + "/" + wav_file_name, callback_param.pcm_data))
        {
            std::cout << "保存音频文件失败，请检查输出文件夹是否存在。" << std::endl;
            break;
        }
    }

    code = BallerTTSSessionEnd(session_id);
    if (BALLER_SUCCESS != code)
    {
        std::cout << "session end failed " << code << std::endl;
        return code;
    }
    std::cout << "session end success" << std::endl;

    return BALLER_SUCCESS;
}

int main(int argc, char** argv)
{
    BallerTTSSetWorkingThreadNumber(4);
    BallerTTSWorkingThread();
    
    if (argc != 5 && argc != 6)
    {
        std::cout << "中文语音合成命令行参数: " << argv[0] << " zho 测试文本.txt out_dir speed [ability_server_addr]" << std::endl;
        std::cout << "英文语音合成命令行参数: " << argv[0] << " eng 测试文本.txt out_dir speed [ability_server_addr]" << std::endl;

        std::cout << "out_dir 位输出音频文件的文件夹，运行前需保证该文件夹已存在。" << std::endl;
        std::cout << "ability_server_addr为可选项，表示能力服务器的地址，如果不填表示使用本地能力" << std::endl;
        return 0;
    }

    language = argv[1];
    if (language == "chs")
    {
        language = "zho";
    }
    text_file_name = argv[2];
    out_dir = argv[3];
    speed = std::stof(argv[4]);
    if (argc == 6)
    {
        ability_server = argv[5];
    }

    std::stringstream ss;
    ss << "org_id=" << kOrgId << ",app_id=" << kAppId << ",app_key=" << kAppKey;
    ss << ",license=./license/baller_sdk.license";
    ss << ",log_level=info,log_path=./logs";
    if (!ability_server.empty())
    {
        ss << ",ability_server=" << ability_server;
    }
    printf("login param: %s\n", ss.str().c_str());
    int code = BallerLogin(ss.str().c_str());
    if (BALLER_SUCCESS != code)
    {
        std::cout << "login failed " << code << std::endl;
        return 0;
    }
    std::cout << "login success" << std::endl;

    DoTTS();

    code =BallerLogout();
    if (BALLER_SUCCESS != code)
    {
        std::cout << "logout failed " << code << std::endl;
        return 0;
    }
    std::cout << "logout success" << std::endl;
}
