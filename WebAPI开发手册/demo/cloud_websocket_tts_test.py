#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
python 3.7

# websocket-client版本要求不低于 0.57.0
pip install websocket-client==0.57.0
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
request_url = "ws://api.baller-tech.com/v1/service/ws/v1/tts"
host = "api.baller-tech.com"

# 测试使用的文本数据
txt_file = ""
# 测试的语种
language = ""
# 合成的音频文件格式
# 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
sample_format = "audio/L16;rate=16000"
# 合成的音频的压缩类型
# 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的音频编码”章节
audio_encode = "raw"


def on_error(ws, error):
    print(f"ERROR: {error}")


# 一次语音合成任务只需发送一次文本数据，但合成的结果会分多次返回。
# 服务端会将传入的文本分为不同的子句，每次on_message触发时，返回的是一个子句的合成结果，当收到一个子句的合成结果时，应用就可以开始播放，下一个子句的合成结果会在当前子句播放完成前返回。
def on_message(ws, message):
    try:
        message_values = json.loads(message)
    except Exception as e:
        print(f"load message {message} failed {e}")
        return

    # task_id只在服务器推送的第一帧中出现
    if "task_id" in message_values:
        ws.result_file = open(f"{message_values['task_id']}.{audio_encode}", "wb")
        print(f"task id {message_values['task_id']}")

    if 0 != message_values["code"]:
        print(f"tts failed {message_values['code']} {message_values['message']}")
        return

    # 将合成结果保存到文件
    if message_values["data"]:
        if ws.result_file:
            ws.result_file.write(base64.b64decode(message_values['data']))

    # 最后一帧时关闭WebSocket连接
    if 1 == message_values["is_end"]:
        ws.close()


def on_open(ws):
    def run(**kwargs):
        with open(txt_file, "rb") as fp:
            txt_data = fp.read()
            fp.close()

        # 业务参数
        business_params = {
            "language": language,
            "sample_format": sample_format,
            "audio_encode": audio_encode,
        }
        data_params = {
            "txt": base64.b64encode(txt_data).decode(encoding='utf-8'),
        }
        params = {"data": data_params, "business": business_params}
        ws.send(json.dumps(params))

    thread.start_new_thread(run, ())


def test_ws():
    date = formatdate(timeval=None, localtime=False, usegmt=True)

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
