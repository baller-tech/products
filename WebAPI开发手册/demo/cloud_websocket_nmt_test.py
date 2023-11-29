#!/usr/bin/env python
# -*- coding:utf-8 -*-

"""
python 3.7

# websocket-client版本要求不低于 0.57.0
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
request_url = "ws://api.baller-tech.com/v1/service/ws/v1/mt"
host = "api.baller-tech.com"

# 测试语种
# tib-chs: 藏文翻译为中文
language = "tib-chs"
# 存储测试数据的文件
txt_file = ""
# 结果是否保存到文件中
save_to_file = True


def on_error(ws, error):
    print(f"ERROR: {error}")


def on_message(ws, message):
    try:
        message_values = json.loads(message)
    except Exception as e:
        print(f"load message {message} failed {e}")
        return

    if "task_id" in message_values:
        print(f"task id {message_values['task_id']}")

    if 0 != message_values["code"]:
        print(f"nmt failed {message_values['code']} {message_values['message']}")
    else:
        # 获取结果
        if message_values["data"]:
            result = message_values['data']
            if ws.out_file:
                ws.out_file.write(result)
                ws.out_file.flush()
            print(result)

    # 最后一帧时关闭WebSocket连接
    if 1 == message_values["is_end"]:
        ws.close()


def on_open(ws):
    def run(**kwargs):
        with open(txt_file, "rb") as fp:
            txt_data = fp.read()
            fp.close()

        ws.out_file = None
        if save_to_file:
            out_file_name = txt_file + ".out.txt"
            ws.out_file = open(out_file_name, "w", encoding='utf-8')

        # 业务参数
        business_params = {
            "language": language,
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
