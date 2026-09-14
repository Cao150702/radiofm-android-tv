#!/usr/bin/env bash
# 启动 web 预览：本地静态服务 + 公网隧道
#
#   ./serve.sh              仅本地（http://127.0.0.1:8099，HTTP 电台全部可播）
#   ./serve.sh --tunnel     本地 + cloudflared 公网隧道（https，纯 HTTP 电台会被浏览器拦）
#
# 注意：--tunnel 用的是 cloudflared 临时域名，每次重启都会变。

set -euo pipefail
cd "$(dirname "$0")"

PORT="${PORT:-8099}"
TUNNEL=0
[ "${1:-}" = "--tunnel" ] && TUNNEL=1

# 端口占用检查
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "端口 $PORT 已被占用，直接复用。"
else
  # 用 devserver.py 而不是 python -m http.server —— 它会发 no-store，
  # 免得浏览器拿缓存的旧代码，改了看不见。
  (python3 devserver.py "$PORT" > /tmp/radiofm-http.log 2>&1 &)
  sleep 1.5
  echo "静态服务已启动（no-cache）：http://127.0.0.1:$PORT/"
fi

echo
echo "本地预览：http://127.0.0.1:$PORT/"
echo "  · 这个地址下 http:// 的电台也能播（浏览器只拦 https 页面里的 http 资源）"

if [ "$TUNNEL" = "1" ]; then
  if ! command -v cloudflared >/dev/null 2>&1; then
    echo "cloudflared 未安装：brew install cloudflared" >&2
    exit 1
  fi
  (cloudflared tunnel --url "http://127.0.0.1:$PORT" --no-autoupdate > /tmp/radiofm-cf.log 2>&1 &)
  sleep 12
  PUB=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' /tmp/radiofm-cf.log | head -1 || true)
  if [ -n "$PUB" ]; then
    echo
    echo "公网地址：$PUB"
    echo "  · https 页面下，纯 HTTP 的电台（AsiaFM 全系、Lam Rim、Curiosity）会被浏览器"
    echo "    以「混合内容」拦掉，列表里标了「⚠ 仅 HTTP」，点开会提示，不会静默失败。"
    echo "  · ICY 曲目信息在公网域名下大概率取不到（CORS），属预期，不影响播放。"
  else
    echo "隧道启动失败，看 /tmp/radiofm-cf.log" >&2
  fi
fi

echo
echo "停止： pkill -f 'devserver.py $PORT' ; pkill -f 'cloudflared tunnel --url http://127.0.0.1:$PORT'"
