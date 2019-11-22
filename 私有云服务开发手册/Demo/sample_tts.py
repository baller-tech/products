#!/usr/bin/env python
# -*- coding:utf-8 -*-
import base64
import json
import time
import uuid
import requests

# 请求服务的地址
url = 'http://ip:port/v1/service/private/v1/tts'
# 回调使用的地址
callback_url = 'http://xxxx'
# 测试使用的文本文件的路径
test_txt = "باتارىيەنىڭ ئۆزىنىلا ئەۋەتەمدىم ياكى قوشۇپ بەرگەن نەرسىلەرنىمۇ ئەۋەتەمدىم"
# 确定是否使用回调机制 True: 使用回调机制 False：使用轮询查询
is_use_callback = False


def test_tts_put(id, body, is_use_callback):
    """
    :param id: 请求ID
    :param body: 文本信息
    :return: 处理结果码
    """
    business_params = {
        'audio_format': 'audio/L16;rate=16000',
        'request_id': str(id),
        'input_mode': "once",
        'language': 'uig'
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


def test_tts_get(id, pcm_file):
    """
    :param id: 请求ID
    :param pcm_file: pcm保存文件
    :return: True：不需继续获取结果 False：需要继续获取结果
    """
    business_params = {
        'request_id': str(id),
    }
    business_params_base64 = base64.b64encode(json.dumps(business_params).encode(encoding='utf-8')).decode()

    headers = {
        'B-CurTime': str(int(time.time())),
        'B-Param': business_params_base64,
    }
    
    r = requests.get(url, headers=headers)

    if r.headers["Content-Type"] == "audio/mpeg" and int(r.headers["B-Code"]) == 0:
        pcm_file.write(r.content)
        return int(r.headers["B-Is-End"]) == 1
    else:
        return True

    return True


if __name__ == '__main__':
    request_id = str(uuid.uuid4())

    ret = test_tts_put(request_id, test_txt.encode(), is_use_callback)
    if 0 == ret:
        out_file = open(request_id + ".pcm", "wb")

        while not is_use_callback:
            if test_tts_get(request_id, out_file):
                break
            time.sleep(0.15)

        out_file.close()
    else:
        print(f"put is failed {ret}")
