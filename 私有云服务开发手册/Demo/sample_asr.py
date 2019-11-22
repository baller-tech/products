#!/usr/bin/env python
# -*- coding:utf-8 -*-
import base64
import json
import time
import uuid
import requests

# 请求服务的地址
url = 'http://ip:port/v1/service/private/v1/asr'
# 回调使用的地址
callback_url = 'http://xxxx'
# 测试使用的pcm文件路径
pcm_file_path = "xxxxx.pcm"
# 确定是否使用回调机制 True: 使用回调机制 False：使用轮询查询
is_use_callback = False


def test_asr_put(request_id, input_mode, body, is_use_callback):
    """
    :param request_id: 请求ID
    :param input_mode: 模式
    :param body: 音频数据
    :param is_use_callback: 使用使用回调机制获取识别结果
    :return: 处理结果码
    """
    business_params = {
        'audio_format': 'audio/L16;rate=16000',
        'request_id': str(request_id),
        'input_mode': input_mode,
        'language': "mon",
    }

    # 使用回调机制时需添加回调的地址
    if is_use_callback:
        business_params["callback_url"] = callback_url

    business_params_base64 = base64.b64encode(json.dumps(business_params).encode(encoding='utf-8')).decode()
    
    headers = {
        'B-CurTime': str(int(time.time())),
        'B-Param': business_params_base64,
        'Content-Type': "application/octet-stream"
    }
    
    r = requests.post(url, headers=headers, data=body)
    
    str_content = r.content.decode()
    json_value = json.loads(str_content)
    return int(json_value["code"])


def test_asr_get(request_id):
    """
    :param request_id: 请求ID
    :return: True: 不需继续调用 False：需继续调用
    """
    business_params = {
        'request_id': str(request_id),
    }
    business_params_base64 = base64.b64encode(json.dumps(business_params).encode(encoding='utf-8')).decode()
    
    headers = {
        'B-CurTime': str(int(time.time())),
        'B-Param': business_params_base64,
    }
    
    r = requests.get(url, headers=headers)
    
    str_content = r.content.decode()
    json_value = json.loads(str_content)
    if int(json_value["code"]) == 0:
        if json_value["data"]:
            # 打印识别结果
            print(json_value["data"])
        return int(json_value["is_end"]) == 1
    else:
        # 接口调用出错 不需在继续获取
        return True
    
    return True


def test_continue(is_use_callback):
    """
    为了模拟从音频设备获取数据，并实时传递给服务器，这里读取一个pcm文件，每次传递40ms的数据给服务器
    :param is_use_callback:  使用使用回调机制获取识别结果
    :return:
    """
    file_pcm = open(pcm_file_path, "rb")
    pcm_data = file_pcm.read()
    
    iOffset = 0
    iPackageSize = 2 * 16 * 4000
    request_id = uuid.uuid4()
    print(request_id)
    while len(pcm_data) > iOffset + iPackageSize:
        body = pcm_data[iOffset: iOffset + iPackageSize]
        ret = test_asr_put(request_id, "continue", body, is_use_callback)
        if 0 != ret:
            print(f"{iOffset} test_asr_put failed f{ret}")
            exit()

        if not is_use_callback:
            if test_asr_get(request_id):
                exit()
        
        iOffset += iPackageSize
    
    if iOffset < len(pcm_data):
        body = pcm_data[iOffset:]
        ret = test_asr_put(request_id, "end", body, is_use_callback)
        if 0 != ret:
            print(f"last test_asr_put failed f{ret}")
            exit()

    while not is_use_callback:
        if test_asr_get(request_id):
            break
        time.sleep(0.15)


def test_once(is_use_callback):
    """
    将音频文件一次性传入web API接口
    :param is_use_callback: 是否使用回调地址
    :return:
    """

    file_pcm = open(pcm_file_path, "rb")
    pcm_data = file_pcm.read()
    
    request_id = uuid.uuid4()
    print(request_id)
    ret = test_asr_put(request_id, "once", pcm_data, is_use_callback)
    if 0 != ret:
        print(f"test_asr_put failed f{ret}")
        exit()

    while not is_use_callback:
        if test_asr_get(request_id):
            break
        time.sleep(0.15)


if __name__ == '__main__':
    print(f"start test continue")
    test_continue(is_use_callback)
    print(f"end test continue")
    print()

    print(f"start test once")
    test_once(is_use_callback)
    print(f"end test once")
    print()
