#!/usr/bin/env python
# -*- coding:utf-8 -*-

import tornado.ioloop
import tornado.web
import json

"""
仅示例使用,调用者需自己实现web服务
"""

class ASRHandler(tornado.web.RequestHandler):
    def post(self):
        result = json.loads(self.request.body.decode())
        print(f"request id: {result['request_id']} "
              f"code: {result['code']} "
              f"is_end: {result['is_end']} "
              f"data: {result['data']}"
              )

        self.write("")
        self.finish()


class TTSHandler(tornado.web.RequestHandler):
    def post(self):
        request_id = self.request.headers["B-Request-Id"]

        print(f"request {request_id} is end: {self.request.headers['B-Is-End']} body len: {len(self.request.body)}")
        open(request_id + ".pcm", "ab").write(self.request.body)

        self.write("")


def make_app():
    return tornado.web.Application([
        (r"/asr/callback", ASRHandler),
        (r"/tts/callback", TTSHandler),
    ])


if __name__ == "__main__":
    app = make_app()
    app.listen(18888)
    tornado.ioloop.IOLoop.current().start()