#!/usr/bin/env python3
"""开发用静态服务器 —— 比 `python -m http.server` 多的唯一一件事：禁用缓存。

为什么需要：预览期间改动很频繁，浏览器的 HTTP 缓存会让你看到旧代码，
排查起来极浪费时间（本项目就踩过一次）。这里给所有响应打上 no-store，
每次刷新都拿最新的。
"""
import sys
import functools
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler


class NoCacheHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

    def log_message(self, fmt, *args):
        # 默认日志太吵，只留错误
        if not str(args[1] if len(args) > 1 else '').startswith('2'):
            super().log_message(fmt, *args)


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    handler = functools.partial(NoCacheHandler, directory='.')
    ThreadingHTTPServer.allow_reuse_address = True
    srv = ThreadingHTTPServer(('127.0.0.1', port), handler)
    print(f'serving on http://127.0.0.1:{port}/  (no-cache)')
    srv.serve_forever()
