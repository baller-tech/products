#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
python 3.7
pip install requests
"""

import base64
import hashlib
import json
import time
import uuid
from email.utils import formatdate

import requests

# 请求的地址
request_url = "http://api.baller-tech.com/v1/service/v1/mt"

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 语种
# tib-chs: 藏文翻译为中文
language = "tib-chs"
# 测试数据存储的文件
test_file = ""
# 是否将结果保存到文件
save_to_file = True


def post_data(request_id, data):
    """
    向服务器发送测试数据
    :param request_id: 请求ID
    :param data: 测试数据
    :return: True：成功；False：失败
    """
    business_params = {
        'request_id': str(request_id),
        'language': language,
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


def get_result(request_id, out_file):
    """
    获取翻译结果
    :param request_id: 请求ID
    :param out_file: 输出结果保存的文件
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
    if response_content['data']:
        print(f"{response_content['data']}")
        if out_file:
            out_file.write(response_content['data'])

    return 1 == int(response_content["is_end"])


def test_once():
    # 读取测试数据
    with open(test_file, "rb") as file:
        test_data = file.read()
    if not test_data:
        print(f"read test file {test_file} failed")
        return

    out_file = None
    if save_to_file:
        out_file_name = test_file + "_once.txt"
        out_file = open(out_file_name, "w", encoding='utf-8')

    # 发送测试数据
    request_id = str(uuid.uuid1())
    put_success = post_data(request_id, test_data)
    if not put_success:
        print(f"{request_id} POST data failed")
        return

    # 获取结果
    is_end = False
    while not is_end:
        is_end = get_result(request_id, out_file)
        time.sleep(0.150)
    print(f"{request_id} GET result finished")


if __name__ == '__main__':
    test_once()
