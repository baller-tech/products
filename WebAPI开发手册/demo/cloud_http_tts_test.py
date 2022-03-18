#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
python 3.7

# requests版本要求不低于 2.23.0
pip install requests==2.230
"""

import base64
import hashlib
import json
import time
import uuid
from email.utils import formatdate

import requests

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 请求的地址
request_url = "http://api.baller-tech.com/v1/service/v1/tts"
# 测试使用的文本数据及参数
txt_file = ""
# 测试的语种
language = ""
# 合成的音频文件格式
# 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
sample_format = "audio/L16;rate=16000"
# 合成语速
# 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“语速的取值范围”章节
speed = 1.0
# 合成的音频的压缩类型
# 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的音频编码”章节
audio_encode = "raw"
# 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
callback_url = ""


def post_data(request_id, data):
    """
    向服务器端发送文字数据
    :param request_id: 请求ID
    :param data: 文字数据
    :return: True：成功；False：失败
    """
    business_params = {
        'request_id': str(request_id),
        'language': language,
        'sample_format': sample_format,
        'audio_encode': audio_encode,
        "speed": speed,
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
        print(f"{request_id} POST failed {e}")
        return False

    if 0 != response_content["code"]:
        print(f"{request_id} POST failed {response_content['code']} {response_content['message']} ")
        return False

    return True


def get_result(request_id, pcm_file):
    """
    获取合成结果
    :param request_id: 请求ID
    :param pcm_file: 合成音频保存的文件
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
    code = response.headers["B-Code"]
    if 0 == int(code):
        pcm_file.write(response.content)
    else:
        print(f"{request_id} GET failed {response.headers['B-Code']} {response.headers['B-Message']}")
    return 1 == int(response.headers["B-Is-End"])


def main():
    # 读取文本数据
    # 一次语音合成任务需调用一次POST接口，和多次GET接口。
    # 调用POST接口将文本数据发送给服务器，服务端会将传入的文本分为不同的子句，GET接口的响应报文中为一个子句的合成结果，当收到一个子句的合成结果时，应用就可以开始播放，下一个子句的合成结果会在当前子句播放完成前返回。
    with open(txt_file, "rb") as file:
        txt_data = file.read()
    if not txt_data:
        print(f"read txt file {txt_file} failed")
        return

    # 发送文本数据
    request_id = str(uuid.uuid1())
    put_success = post_data(request_id, txt_data)
    if not put_success:
        print(f"{request_id} POST data failed")
        return

    # 获取合成结果
    if not callback_url:
        is_end = False
        pcm_file = open(f"{request_id}.{audio_encode}", "wb")
        while not is_end:
            is_end = get_result(request_id, pcm_file)
            time.sleep(0.150)
        print(f"{request_id} GET result finished")


if __name__ == '__main__':
    main()
