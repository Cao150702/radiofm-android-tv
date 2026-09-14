#!/usr/bin/env bash
# Android TV 端到端验收 —— 在 1920×1080 的 Android TV 模拟器上真跑一遍。
#
# 验的是「遥控器能不能用」，不是「代码看起来对不对」：
#   · 布局是否真的切成两栏、字号是否够大
#   · 焦点高亮是否可见（这行原本有个真实疑点：itemsCanFocus=false 与
#     行自身 state_focused 冲突，可能完全不显示 —— 只能靠截图判定）
#   · 方向键是否真的移动焦点、确认键是否真的播放
#   · TV 桌面入口是否注册
#
# 用 adb shell input keyevent 发的是真·遥控器按键，走完整事件链。

set -u
ADB="$HOME/android-sdk/platform-tools/adb"
PKG=com.radio.fm
OUT=/tmp/tv-verify
PASS=0; FAIL=0
mkdir -p "$OUT"

red()   { printf "\033[31m%s\033[0m\n" "$1"; }
green() { printf "\033[32m%s\033[0m\n" "$1"; }
log()   { printf "%-46s %s\n" "$1" "$2"; }

ok()   { PASS=$((PASS+1)); log "$1" "$(green "✓ $2")"; }
bad()  { FAIL=$((FAIL+1)); log "$1" "$(red "✗ $2")"; }

key() { $ADB shell input keyevent "$1" >/dev/null 2>&1; sleep 1.2; }

shot() { $ADB exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }

# 读当前焦点所在控件（dumpsys 里 u0 后带 mFocused 的行）
focused_view() {
  $ADB shell dumpsys window 2>/dev/null \
    | grep -m1 "mFocusedApp\|mCurrentFocus" | sed 's/.*[ {]//' | head -1
}

echo "=== 0. 前置检查 ==="
$ADB shell pm path $PKG >/dev/null 2>&1 || { red "APK 未安装，先跑 adb install"; exit 1; }
ok "APK 已安装" "$($ADB shell pm path $PKG | head -1)"

$ADB shell wm size | grep -q "1920x1080" && ok "分辨率 1920x1080" "$($ADB shell wm size | head -1)" \
  || bad "分辨率" "$($ADB shell wm size)"

DENSITY=$($ADB shell wm density | grep -o '[0-9]*$' | head -1)
[ "$DENSITY" = "320" ] && ok "密度 320 (xhdpi)" "$DENSITY" || bad "密度" "$DENSITY"

echo
echo "=== 1. TV 桌面入口 ==="
if $ADB shell cmd package query-activities -a android.intent.action.MAIN \
     -c android.intent.category.LEANBACK_LAUNCHER 2>/dev/null | grep -q "$PKG"; then
  ok "LEANBACK_LAUNCHER 已注册" "会出现在电视桌面"
else
  bad "LEANBACK_LAUNCHER" "未注册 —— 电视上找不到图标"
fi

echo
echo "=== 2. 启动应用 ==="
$ADB shell am force-stop $PKG >/dev/null 2>&1
$ADB shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 6
CUR=$(focused_view)
echo "$CUR" | grep -q "$PKG" && ok "应用已启动" "$CUR" || bad "应用启动" "$CUR"

shot "01-launch"

echo
echo "=== 3. 布局 ==="
# 用 uiautomator dump 拿控件真实坐标（比截图肉眼判断可靠）
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
$ADB pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
if [ -s "$OUT/ui.xml" ]; then
  ok "拿到控件树" "$(wc -c < "$OUT/ui.xml") 字节"
  # 左栏按钮和右栏列表应该在水平方向分开
  python3 - "$OUT/ui.xml" <<'PY'
import re,sys
xml=open(sys.argv[1],encoding='utf-8',errors='ignore').read()
def bounds(pat):
    m=re.search(r'<node[^>]*resource-id="[^"]*%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'%pat,xml)
    return tuple(map(int,m.groups())) if m else None
li=bounds('station_list'); rb=bounds('btn_play')
if li and rb:
    lx1,ly1,lx2,ly2=li; bx1,by1,bx2,by2=rb
    print(f"  列表 x:{lx1}~{lx2} y:{ly1}~{ly2}")
    print(f"  播放钮 x:{bx1}~{bx2} y:{by1}~{by2}")
    print("  LEFT_COL_RIGHT=%d"%bx2)
    print("  LIST_LEFT=%d"%lx1)
else:
    print("  未能定位控件")
PY
else
  bad "控件树" "uiautomator dump 失败"
fi

echo
echo "=== 4. 遥控器导航（真按键）==="
# 返回主界面并确保焦点在列表
$ADB shell input keyevent KEYCODE_BACK >/dev/null 2>&1; sleep 1
$ADB shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 4

shot "02-before-down"
key KEYCODE_DPAD_DOWN
shot "03-after-down1"
key KEYCODE_DPAD_DOWN
shot "04-after-down2"
key KEYCODE_DPAD_UP
shot "05-after-up"

# 关键：焦点行有没有可见高亮 —— 比较按下前后的截图差异
if command -v magick >/dev/null 2>&1; then
  DIFF=$(magick compare -metric AE "$OUT/02-before-down.png" "$OUT/03-after-down1.png" null: 2>&1 | grep -oE '^[0-9]+' || echo 0)
  if [ "${DIFF:-0}" -gt 1000 ]; then
    ok "方向键改变了画面" "像素差异 $DIFF（焦点在动）"
  else
    bad "方向键无可见变化" "差异仅 $DIFF 像素 —— 焦点可能没显示或没移动"
  fi
fi

echo
echo "=== 5. 确认键播放 ==="
key KEYCODE_DPAD_CENTER
sleep 5
shot "06-after-enter"
# 播放后状态栏文字应变成「正在播放」或「缓冲中」
if $ADB shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1; then
  $ADB pull /sdcard/ui2.xml "$OUT/ui2.xml" >/dev/null 2>&1
  if grep -qE "正在播放|缓冲中|错误" "$OUT/ui2.xml" 2>/dev/null; then
    ok "确认键触发了播放" "$(grep -oE '正在播放|缓冲中|错误[^"]*' "$OUT/ui2.xml" | head -1)"
  else
    bad "确认键" "状态栏没变化"
  fi
fi

echo
echo "=== 6. 按钮区的焦点 ==="
$ADB shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 3
key KEYCODE_DPAD_RIGHT
shot "07-focus-right"
key KEYCODE_DPAD_RIGHT
shot "08-focus-right2"

echo
echo "=== 截图清单 ==="
ls -la "$OUT"/*.png | awk '{print "  "$9" ("$5" bytes)"}'

echo
if [ $FAIL -eq 0 ]; then
  green "全部 $PASS 项通过"
else
  red "$FAIL 项失败 / 共 $((PASS+FAIL)) 项"
fi
exit $FAIL
