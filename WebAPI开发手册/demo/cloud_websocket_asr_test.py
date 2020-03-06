#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
python 3.7
pip install websocket-client
"""

import websocket
from email.utils import formatdate
import hmac
from hashlib import sha256
import base64
import json
from urllib.parse import urlencode
import _thread as thread

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 请求的地址
request_url = "ws://api.baller-tech.com/v1/service/ws/v1/asr"
host = "api.baller-tech.com"

# 测试使用的音频数据
pcm_file = ""
language = ""
audio_format = "audio/L16;rate=16000"


def on_error(ws, error):
    print(f"ERROR: {error}")


def on_message(ws, message):
    try:
        message_values = json.loads(message)
    except Exception as e:
        print(f"load message {message} failed {e}")
        return

    # task_id只在服务器推送的第一帧中出现
    if "task_id" in message_values:
        print(f"task id {message_values['task_id']}")

    if 0 != message_values["code"]:
        print(f"asr failed {message_values['code']} {message_values['message']}")
        return

    # 语音识别时，会将传入的语音根据一定的规则分为不同的子句，每次GET请求返回的一个子句的识别结果
    # 一个子句的识别结果有两种状态完整的识别结果(is_complete等于1)和不完整的识别结果(is_complete等于0)；
    # 不完整的识别结果表示本次获取的识别结果并不是该子句的最终结果，下一次GET请求获取的识别结果还是该子句的；
    # 完整的识别结果表示本次获取的结果是该子句的最终结果，下一次GET请求获取的结果是下一个子句的结果；
    # 大部分的使用场景下我们只需要关心每个子句的完整识别结果即可；
    # 在一些实时的ASR识别系统中，为了让用户更早的看到识别结果，可以将子句的非最终结果上屏，并不断更新显示该子句的识别结果，
    # 当获取到该子句的完整识别结果时，在将完整的子句识别结果上屏，这样用户体验会更好。

    # 打印识别的结果
    if message_values["data"]:
        print(f"{message_values['is_complete']}: {message_values['data']}")

    # 最后一帧时关闭WebSocket连接
    if 1 == message_values["is_end"]:
        ws.close()


def on_open(ws):
    def run_continue(**kwargs):
        frame_size = 40 * 16 * 2  # 每一帧时发送的音频时长

        with open(pcm_file, "rb") as fp:
            pcm_data = fp.read()
            fp.close()

        # 业务参数
        business_params = {
            "language": language,
            "audio_format": audio_format,
        }

        send_size = 0
        send_frame_count = 0
        # 连续发送音频数据
        while len(pcm_data) - send_size > frame_size:
            data_params = {
                "input_mode": "continue",
                "audio": base64.b64encode(pcm_data[send_size: send_size + frame_size]).decode(encoding='utf-8'),
            }
            send_size += frame_size

            params = {"data": data_params}
            if 0 == send_frame_count:
                params.update(business=business_params)
            send_frame_count += 1
            ws.send(json.dumps(params))

        if len(pcm_data) - send_size > 0:
            # 发送最后一帧音频数据
            data_params = {
                "input_mode": "end",
                "audio": base64.b64encode(pcm_data[send_size:]).decode(encoding='utf-8'),
            }

            params = {"data": data_params}
            if 0 == send_frame_count:
                params.update(business=business_params)
            send_frame_count += 1
            ws.send(json.dumps(params))

    thread.start_new_thread(run_continue, ())


def test_ws():
    date = formatdate(timeval=None, localtime=True, usegmt=True)

    # signature
    signature_org = ""
    signature_org += "app_id:" + str(app_id) + "\n"
    signature_org += "date:" + date + "\n"
    signature_org += "host:" + host
    signature_sha = hmac.new(app_key.encode(encoding='utf-8'),
                             signature_org.encode(encoding='utf-8'),
                             digestmod=sha256
                             ).digest()
    signature_final = base64.b64encode(signature_sha).decode(encoding='utf-8')

    # authorization
    authorization_org = {
        "app_id": str(app_id),
        "signature": signature_final,
    }
    authorization_org = json.dumps(authorization_org).encode(encoding='utf-8')
    authorization_final = base64.b64encode(authorization_org).decode(encoding='utf-8')

    url_params = {
        "date": date,
        "host": host,
        "authorization": authorization_final
    }
    url_with_param = request_url + "?" + urlencode(url_params)

    websocket.enableTrace(False)
    ws = websocket.WebSocketApp(url_with_param,
                                on_message=on_message,
                                on_error=on_error,
                                on_open=on_open)

    ws.run_forever()


if __name__ == '__main__':
    test_ws()
