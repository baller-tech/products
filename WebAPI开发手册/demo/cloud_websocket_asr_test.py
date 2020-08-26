#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
1. 运行环境准备:
    python 3.7
2. 安装第三方依赖库
    pip install websocket-client==0.57.0
3. 运行程序
    python cloud_websocket_asr_test.py
"""

import os
import websocket
from email.utils import formatdate
import hmac
from hashlib import sha256
import base64
import json
from urllib.parse import urlencode
import _thread as thread
import time

# 请求的地址
request_url = "ws://api.baller-tech.com/v1/service/ws/v1/asr"
host = "api.baller-tech.com"

# 由北京市大牛儿科技发展有限公司统一分配
org_id = 0
app_id = 0
app_key = ""

# 测试使用的音频数据
audio_file = r""
# 音频格式
# 请查考《语音识别（ASR）WebSocket协议WebAPI开发文档.pdf》中“支持的音频格式”章节
audio_format = "raw"
# 语种
# 请查考《语音识别（ASR）WebSocket协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
language = ""
# 采样率
# 请查考《语音识别（ASR）WebSocket协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
sample_rate = 16000
sample_format = "audio/L16;rate=" + str(sample_rate)
# 服务类型
# sentence: 整句识别 结果实时返回 每个任务限制时长
# realtime: 实时识别 结果实时返回 每个任务无时长限制
service_type = "sentence"
# 结果是否保存到文件中
save_to_file = True
# 是否显示子句的位移信息
show_offset = False


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

    # 最后一帧时关闭WebSocket连接
    if 1 == message_values["is_end"]:
        ws.close()

    # 语音识别时，会将传入的语音根据一定的规则分为不同的子句，每次GET请求返回的一个子句的识别结果
    # 一个子句的识别结果有两种状态完整的识别结果(is_complete等于1)和不完整的识别结果(is_complete等于0)；
    # 不完整的识别结果表示本次获取的识别结果并不是该子句的最终结果，下一次GET请求获取的识别结果还是该子句的；
    # 完整的识别结果表示本次获取的结果是该子句的最终结果，下一次GET请求获取的结果是下一个子句的结果；
    # 大部分的使用场景下我们只需要关心每个子句的完整识别结果即可；
    # 在一些实时的ASR识别系统中，为了让用户更早的看到识别结果，可以将子句的非最终结果上屏，并不断更新显示该子句的识别结果，
    # 当获取到该子句的完整识别结果时，在将完整的子句识别结果上屏，这样用户体验会更好。

    # 打印识别的结果
    if message_values["data"]:
        if message_values['is_complete']:
            print(f"{message_values['data']}")
            if ws.out_file:
                ws.out_file.write(message_values['data'])
            if show_offset:
                print(f"begin = {message_values['begin']} ms end = {message_values['end']} ms")


def on_open(ws):
    def run_continue(**kwargs):
        frame_size = 400 * 16 * 2  # 每一帧发送400ms的音频数据

        with open(audio_file, "rb") as fp:
            pcm_data = fp.read()
            fp.close()

        if save_to_file:
            out_file_name = audio_file + ".txt"
            ws.out_file = open(out_file_name, "w", encoding='utf-8')

        # 业务参数
        business_params = {
            "language": language,
            "sample_format": sample_format,
            "audio_format": audio_format,
            "service_type": service_type,
            "vad": "on",
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

            # 停留40ms的作用：
            # 1. 模拟人说话时间间隙。
            # 2. 避免将音频数据瞬时全部发送到服务器而导致服务器任务缓存区满返回51024错误码的情况。
            time.sleep(0.04)

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
    if len(audio_file) <= 3:
        print(f"输入的音频文件({audio_file})无效")
        exit(0)

    suffix = os.path.splitext(audio_file)[-1][1:]
    if not suffix:
        print(f"未知格式的音频文件({audio_file})")
        exit(0)

    suffix = suffix.lower()
    if suffix not in ("mp3", "wav", "m4a", "ogg", "pcm"):
        print(f"未知格式的音频文件({audio_file})")
        exit(0)

    test_ws()
