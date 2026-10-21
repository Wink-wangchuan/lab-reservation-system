#!/usr/bin/env python3
"""智慧实验室预约管理系统 · 前端静态服务（SPA 回退）

仅监听 tailnet 地址（默认 100.96.0.100），绝不绑定 0.0.0.0。
路由使用 history 模式，故对不存在的路径统一回退到 index.html。

用法：
    python3 serve_dist.py --bind 100.96.0.100 --port 8092 --directory ~/lab-reservation/dist
"""
import argparse
import functools
import os
import posixpath
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote


class SpaHandler(SimpleHTTPRequestHandler):
    """命中真实文件则原样返回，否则回退 index.html"""

    def send_head(self):
        path = self.translate_path(self.path)
        if not os.path.exists(path) or os.path.isdir(path):
            self.path = '/index.html'
        return super().send_head()

    def translate_path(self, path):
        # 去掉查询串与锚点后安全拼接，避免路径穿越
        path = unquote(path.split('?', 1)[0].split('#', 1)[0])
        words = [w for w in posixpath.normpath(path).split('/') if w and w not in ('.', '..')]
        result = self.directory
        for word in words:
            result = os.path.join(result, word)
        return result

    def end_headers(self):
        # 缓存策略：
        #  - index.html / 入口 html 不缓存，否则重新部署后浏览器可能仍拿旧 HTML，
        #    进而引用到已不存在的旧哈希资源（表现为"改了样式却看不到"）
        #  - /assets/ 下的文件名带内容哈希，可长期强缓存，减少重复传输
        path = self.path.split('?', 1)[0]
        if path.endswith('.html') or path == '/':
            self.send_header('Cache-Control', 'no-cache, must-revalidate')
        elif '/assets/' in path:
            self.send_header('Cache-Control', 'public, max-age=31536000, immutable')
        else:
            self.send_header('Cache-Control', 'no-cache')
        super().end_headers()

    def log_message(self, fmt, *args):
        # 简洁访问日志，交由 systemd 采集
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bind', default='100.96.0.100')
    parser.add_argument('--port', type=int, default=8092)
    parser.add_argument('--directory', required=True)
    args = parser.parse_args()

    handler = functools.partial(SpaHandler, directory=os.path.expanduser(args.directory))
    httpd = ThreadingHTTPServer((args.bind, args.port), handler)
    print(f'serving {args.directory} on http://{args.bind}:{args.port}', flush=True)
    httpd.serve_forever()


if __name__ == '__main__':
    main()
