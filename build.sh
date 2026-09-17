#!/usr/bin/env bash
# 构建 APK。
#
# 为什么不直接用 gradle：
# 本机的 Gradle 增量构建状态很容易坏掉 —— 报错形如
#   Cannot access output property 'xxx' of task ':app:yyy'.
#   Accessing unreadable inputs or outputs is not supported.
#   > Failed to create MD5 hash for file content.
# 典型诱因是 app/build 下的中间产物被外部动过（手动删子目录、构建中途被打断等）。
# 一旦损坏，后续每次构建都在同一个任务上失败，而错误信息完全指不到真正的原因。
#
# 这个脚本的做法：先正常构建；失败且报的是上述症状时，自动清掉 app/build 重来一次。
# 全量构建约 30 秒，比人工排查快得多。
#
# 用法：./build.sh [额外 gradle 参数]

set -uo pipefail
cd "$(dirname "$0")"

GRADLE="${GRADLE:-/tmp/gradle-dist/gradle-8.7/bin/gradle}"
[ -x "$GRADLE" ] || GRADLE="$(command -v gradle)" || {
  echo "找不到 gradle。设 GRADLE 环境变量，或确保 /tmp/gradle-dist/gradle-8.7 存在。" >&2
  exit 1
}

LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

# 不加 --offline：全新克隆时 AGP 插件还没进 Gradle 缓存，离线模式会直接失败。
# 有缓存时联网构建也只是查一下版本，代价可忽略。
run_build() {
  "$GRADLE" assembleDebug "$@" > "$LOG" 2>&1
}

echo "→ 构建中…"
if run_build "$@"; then
  echo "✓ 构建成功"
  ls -la app/build/outputs/apk/debug/app-debug.apk
  exit 0
fi

# 判断是不是增量状态损坏
if grep -q "Failed to create MD5 hash for file content" "$LOG" \
   || grep -q "Accessing unreadable inputs or outputs is not supported" "$LOG"; then
  echo "⚠ 检测到 Gradle 增量状态损坏，清理 app/build 后重试…"
  rm -rf app/build
  if run_build "$@"; then
    echo "✓ 清理后构建成功"
    ls -la app/build/outputs/apk/debug/app-debug.apk
    exit 0
  fi
fi

echo "✗ 构建失败：" >&2
grep -A 15 "What went wrong\|error:" "$LOG" | head -40 >&2
exit 1
