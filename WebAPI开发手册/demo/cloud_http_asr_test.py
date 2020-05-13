#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
1. 运行环境准备(需要使用conda中的python37)
    conda：下载地址：https://docs.conda.io/en/latest/miniconda.html
2. 创建虚拟环境
    conda create -n baller_asr_test
3. 进入虚拟环境
    Linux:  source activate baller_asr_test
    Windows: activate baller_asr_test
4. 安装第三方依赖库
    pip install requests
    pip install pysoundfile
    conda install -c conda-forge librosa ffmpeg
5. 运行程序
    python cloud_websocket_asr_test.py
"""

import base64
import hashlib
import json
import os
import time
import uuid
from email.utils import formatdate
import time
import requests
import soundfile
import librosa

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 请求的地址
request_url = "http://api.baller-tech.com/v1/service/v1/asr"
# 测试使用的音频数据
audio_file = ""
language = ""
audio_format = "audio/L16;rate=16000"
# 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
callback_url = ""


def audio_to_pcm_16k16bit(file_name):
    """
    将音频文件转换为pcm，并保存到源音频文件同级的目录中
    :param file_name: 音频文件路径
    :return: 转换后的pcm文件路径
    """
    sig, sr = librosa.load(file_name)
    # mono
    if sig.ndim > 1:
        sig = sig[:, 0]
    # 16K
    assert sr >= 16000, sr
    if sr > 16000:
        sig = librosa.resample(y=sig, orig_sr=sr, target_sr=16000, res_type='kaiser_best')
    # write

    pcm_16k16bit_file = file_name[:file_name.rfind(".")] + '.pcm'
    soundfile.write(file=pcm_16k16bit_file, data=sig, samplerate=16000, format='RAW', subtype='PCM_16')
    return pcm_16k16bit_file


def post_data(request_id, input_mode, data):
    """
    向服务器端发送音频数据
    :param request_id: 请求ID
    :param input_mode: 输入模式
    :param data: 音频数据
    :return: True：成功；False：失败
    """
    business_params = {
        'request_id': str(request_id),
        'language': language,
        'audio_format': audio_format,
        'input_mode': input_mode,
        'vad': 'on'
    }
    if callback_url:
        business_params["callback_url"] = callback_url
    business_params_base64 = base64.b64encode(json.dumps(business_params).encode(encoding='utf-8')).decode()

    request_time = formatdate(timeval=None, localtime=False, usegmt=True)
    before_md5 = app_key + request_time + business_params_base64
    hl = hashlib.md5()
    hl.update(before_md5.encode(encoding='utf-8'))
    headers = {
        'B-AppId': str(app_id),
        'B-CurTime': request_time,
        'B-Param': business_params_base64,
        'B-CheckSum': hl.hexdigest(),
        'Content-Type': "application/octet-stream"
    }

    response = requests.post(request_url, headers=headers, data=data)
    try:
        response_content = json.loads(response.content)
    except Exception as e:
        print(f"{request_id} load POST response {response.content} failed {e}")
        return False

    if 0 != response_content["code"]:
        print(f"{request_id} POST failed {response_content['code']} {response_content['message']} ")
        return False

    return True


def get_result(request_id):
    """
    获取合成结果
    :param request_id: 请求ID
    :return: True：结果获取结束；False：结果获取未结束
    """
    business_params = {
        'request_id': request_id,
    }
    business_params_base64 = base64.b64encode(json.dumps(business_params).encode(encoding='utf-8')).decode()

    request_time = formatdate(timeval=None, localtime=False, usegmt=True)
    before_md5 = app_key + request_time + business_params_base64
    hl = hashlib.md5()
    hl.update(before_md5.encode(encoding='utf-8'))
    headers = {
        'B-AppId': str(app_id),
        'B-CurTime': request_time,
        'B-Param': business_params_base64,
        'B-CheckSum': hl.hexdigest(),
    }

    response = requests.get(request_url, headers=headers)
    try:
        response_content = json.loads(response.content)
    except Exception as e:
        print(f"{request_id} load GET response {response.content} failed {e}")
        return True

    if 0 != response_content["code"]:
        print(f"{request_id} GET failed {response_content['code']} {response_content['message']} ")

    # 语音识别时，会将传入的语音根据一定的规则分为不同的子句，每次GET请求返回的一个子句的识别结果
    # 一个子句的识别结果有两种状态完整的识别结果(is_complete等于1)和不完整的识别结果(is_complete等于0)；
    # 不完整的识别结果表示本次获取的识别结果并不是该子句的最终结果，下一次GET请求获取的识别结果还是该子句的；
    # 完整的识别结果表示本次获取的结果是该子句的最终结果，下一次GET请求获取的结果是下一个子句的结果；
    # 大部分的使用场景下我们只需要关心每个子句的完整识别结果即可；
    # 在一些实时的ASR识别系统中，为了让用户更早的看到识别结果，可以将子句的非最终结果上屏，并不断更新显示该子句的识别结果，
    # 当获取到该子句的完整识别结果时，在将完整的子句识别结果上屏，这样用户体验会更好。

    if response_content['data']:
        if response_content['is_complete']:
            print(f"{response_content['data']}", flush=True)
        else:
            print(f"{response_content['data']}", end='\r', flush=True)

    return 1 == int(response_content["is_end"])


def test_once():
    # 读取音频数据
    with open(audio_file, "rb") as file:
        pcm_data = file.read()
    if not pcm_data:
        print(f"read pcm file {audio_file} failed")
        return

    # 发送音频数据
    request_id = str(uuid.uuid1())
    put_success = post_data(request_id, "once", pcm_data)
    if not put_success:
        print(f"{request_id} POST data failed")
        return

    # 获取识别结果
    if not callback_url:
        is_end = False
        while not is_end:
            is_end = get_result(request_id)
            time.sleep(0.150)
        print(f"{request_id} GET result finished")

def test_continue():
    # 读取音频数据
    with open(audio_file, "rb") as file:
        pcm_data = file.read()
    if not pcm_data:
        print(f"read pcm file {audio_file} failed")
        return

    # 发送音频数据 每次发送40ms的音频数据
    request_id = str(uuid.uuid1())
    per_size = 40 * 16 * 2  # 每一次请求发送40ms的音频数据
    send_size = 0
    while len(pcm_data) - send_size > per_size:
        # 发送一次音频数据
        put_success = post_data(request_id, "continue", pcm_data[send_size: send_size + per_size])
        send_size += per_size
        if not put_success:
            print(f"{request_id} POST data failed")
            return

        # 获取一次音频数据
        if not callback_url:
            is_end = get_result(request_id)
            if is_end:
                print(f"{request_id} GET data finished")
                return

        # 停留40ms的作用：
        # 1. 模拟人说话时间间隙。
        # 2. 避免将音频数据瞬时全部发送到服务器而导致服务器任务缓存区满返回51024错误码的情况。
        time.sleep(0.04)

    if len(pcm_data) - send_size > 0:
        # 发送本次事务的最后一包数据
        put_success = post_data(request_id, "end", pcm_data[send_size:])
        if not put_success:
            print(f"{request_id} POST data failed")
            return

    # 连续获取一次音频数据
    if not callback_url:
        is_end = False
        while not is_end:
            is_end = get_result(request_id)
            time.sleep(0.150)
        print(f"{request_id} GET result finished")


if __name__ == '__main__':
    if len(audio_file) <= 3:
        print(f"输入的音频文件({audio_file})无效")
        exit(0)

    suffix = os.path.splitext(audio_file)[-1][1:]
    if not suffix:
        print(f"未知格式的音频文件({audio_file})")
        exit(0)

    suffix = suffix.lower()
    if suffix in ("mp3", "wav"):
        audio_file = audio_to_pcm_16k16bit(audio_file)
    elif suffix != "pcm":
        print(f"未知格式的音频文件({audio_file})")
        exit(0)

    test_once()
    test_continue()
