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

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 图片上文本的语种
# chs： 简体中文
# cht： 繁体中文
language = "chs"

# 请求的地址
request_url = "http://api.baller-tech.com/v1/service/v1/ocr"
# 测试使用的图像数据
image_file = ""
# 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
callback_url = ""


def post_data(request_id, data):
    """
    向服务器端发送图像数据
    :param request_id: 请求ID
    :param data: 图像数据
    :return: True：成功；False：失败
    """
    business_params = {
        'request_id': str(request_id),
        'image_mode': 'multi_row',
        "language": language,
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
    获取识别结果
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

    recognition_result = {}
    if 0 == response_content["code"]:
        # 图像识别时，会将图片中的信息按照一定的规则（目前按行）分为不同的子项，每个GET获取的是一个或多个子项的结果
        for sub_item_result in response_content["data"]:
            if sub_item_result["result"]:
                recognition_result[sub_item_result['order']] = sub_item_result['result']

        if recognition_result:
            sorted(recognition_result.keys())
            for _, value in recognition_result.items():
                print(value)
    else:
        print(f"{request_id} GET failed {response_content['code']} {response_content['message']}")
    return 1 == response_content["is_end"]


def main():
    # 读取图像数据
    with open(image_file, "rb") as file:
        image_data = file.read()
    if not image_data:
        print(f"read image file {image_file} failed")
        return

    # 发送图像数据
    request_id = str(uuid.uuid1())
    put_success = post_data(request_id, image_data)
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


if __name__ == '__main__':
    main()
