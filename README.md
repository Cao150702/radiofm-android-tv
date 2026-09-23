# 网络收音机 · Android TV 版

兼容 **Android 4.4+** 的网络收音机，专门为**安卓电视盒子 / 智能电视**适配：
横屏两栏布局、遥控器（D-pad）全功能操作、3 米视距字号。

同时保留完整的手机竖屏布局 —— 同一份代码，系统按屏幕方向自动选布局。

## 构建

```bash
# 需要：JDK 17+、Android SDK（platform-35 + build-tools 35）、Gradle 8.7
./build.sh
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 150KB）

不用 `gradle assembleDebug` 直接构建：本机 Gradle 的增量状态很容易坏
（报 `Failed to create MD5 hash for file content`，错误信息完全指不到真因）。
`build.sh` 会在命中这个症状时自动清 `app/build` 重来。

`local.properties` 里的 `sdk.dir` 指向本机 SDK 路径，已 gitignore。

## 功能

播放/暂停/停止 · 收藏 · 自定义电台 · M3U/PLS 导入 · 在线电台库（radio-browser.info）·
定时关闭 · 定时唤醒 · ICY 曲目信息 · 断流自动重试

## 电视适配要点

### 布局
`res/layout-land/` 下是电视横屏版：左边「收音机面板 + 控制按钮」，右边整块电台列表。
字号按 1080p + 3 米视距放大（电台名 36sp、列表项 24sp、按钮 72dp 高）。
竖屏仍是原来的单列布局，`res/layout/` 里原封不动。

### 遥控器
遥控器在电视上是唯一的输入方式。三个关键点：

1. **列表项要能被方向键选中并高亮。** `ListView` 拿到焦点后由 `listSelector` 画
   「当前行」，行自己的 `state_focused` 在这种模式下不触发 —— 所以 `listSelector`
   必须指向一个看得见的 selector（`@drawable/row_focus`）。
2. **确认键不会触发 `onItemClick`。** 实测方向键能让 `selectedPos` 变化，但
   `KEYCODE_DPAD_CENTER` 按下去列表纹丝不动。必须自己接管：`setOnKeyListener`
   里拦截 CENTER/ENTER，用 `getSelectedItemPosition()` 取当前项。
3. **列表内的 ↑ 用于滚动，焦点出不去。** 所以「往上回到按钮区」这个最自然的
   动作要显式实现：已在第一行还按 ↑ 时，把焦点交给按钮区。

### 无处可用的硬件菜单键
菜单（在线电台库/导入/设置）原本只靠 `onCreateOptionsMenu` + 硬件菜单键，
而绝大多数电视遥控器没有这个键。电视布局里加了「更多 ⋯」按钮，
点击弹出一个 `AlertDialog` 列表（不用 `openOptionsMenu()` —— 实测在电视上
按下去毫无反应）。

### 设置页的时间输入
手机用 `EditText` 敲数字，但遥控器要弹软键盘才能输入，基本没法用。
电视布局换成 ▲▼ 按钮加减（小时/分钟各自一组），改完即生效。

### AndroidManifest 的电视声明
```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.software.leanback"    android:required="false" />
...
android:banner="@drawable/tv_banner"    <!-- 电视桌面图标 -->
<category android:name="android.intent.category.LEANBACK_LAUNCHER" />
```
- `touchscreen required=false` —— 不加，没触摸屏的电视**直接装不上**
- `LEANBACK_LAUNCHER` —— 不加，装上了也**不在电视桌面显示**，只能 adb 启动
- `banner` 是 **320×180 像素**（不是 dp），所以放在 `drawable-xhdpi/`

## 电台源

内置 **1288** 个台，2026-09-23 逐个实测（HLS 台验到**分片级**，只有 playlist 返回 200 不算数）。

| 分类 | 数量 | 说明 |
|---|---|---|
| 省市县 | 880 | 地级市 / 县 / 区台 |
| 央广省级 | 297 | 央广官方 + CCTV/CETV + 各省卫视伴音 + 各省省级台 |
| 网络 | 97 | 网络电台 |
| 港澳台 | 7 | RTHK 六个台 + 香港机场塔台 |
| 国际 | 7 | CRI、RFI、Swiss News、Anison 等 |

按可播性：**942 个** MP3/AAC 直链（Android 4.4 即可播）+ **346 个** HLS
（需 Android 5.0+）。判定由 `Station.isHls()` 从地址推导，不靠单独字段记。

### 核验方法（踩过的坑都在这）

- **必须带 `Stagefright/1.2 (Linux;Android 4.4)` UA** —— 那是 Android MediaPlayer
  的真实 UA。用 curl 默认 UA 会得到假失败（zeno.fm 返 401）。
- **并发 ≤ 4** —— 16 路并发会把出口打爆，得到"全部失败"的假象（实测踩过）。
- **HLS 要验到分片** —— playlist 返回 200 不代表能播：
  实测有一批台 playlist 正常但分片全 403/404（签名过期）。
- **分片地址有三种写法**：绝对 URL、协议相对（`//host/path`）、相对路径。
  把协议相对当相对路径去拼，会拼出 `host/live///other-host/...` 这种坏地址，
  把好台误判成死的（实测误判过 97 个）。
- **直播分片会滚动** —— 判定失败要重取 playlist 再试，单次失败不能下结论。

### ⚠️ 蜻蜓FM 不是「已死」，是 UA 黑名单

`lhttp.qtfm.cn` / `lhttp.qingting.fm` / `lhttp-hw.qtfm.cn` **都能正常播放**。
实测 User-Agent 行为：

| UA | 结果 |
|---|---|
| `curl/8.x`、`Mozilla/*`、`VLC/3.0`、`Stagefright/1.2`、`ExoPlayerLib/2.19`、空 UA | **200** |
| `WinampMPEG/5.09` | **403** |

早期判定「蜻蜓全系已死」是因为探测脚本带了 `WinampMPEG` 的 UA（为了取 ICY 元数据），
于是每个蜻蜓地址都被误判。**维护这个电台表时如果用脚本探测，千万别带 Winamp 的 UA。**

Android `MediaPlayer` 的默认 UA 实测可以通过。

### 已知不可用
- `ngcdn00X.cnr.cn`（央广自有 CDN）：仅 001/002 存活且为 HLS，Android 4.4 不支持
- 各地方台官网流：多为 HLS 或需要 Referer
- `lzlive.vojs.cn`（江苏新闻）：返回 206 但只有 342 字节，实际无音频

## 验收

`verify-tv-android.sh` —— 在 Android TV 模拟器上跑端到端验收：

```bash
# 需要先起模拟器（AVD 名 tv1080，见下）
./verify-tv-android.sh
```

验 8 项：分辨率/密度、`LEANBACK_LAUNCHER` 注册、两栏布局、方向键导航、
确认键播放等。用 `adb shell input keyevent` 发**真按键**，走完整事件链。

### 模拟器环境

```bash
# 镜像：system-images;android-34;android-tv;arm64-v8a   （Apple Silicon 可跑）
~/android-sdk/emulator/emulator -avd tv1080 -no-audio -no-boot-anim -gpu swiftshader_indirect
```

**注意 `adb install` 会卡死**（实测挂了 40 分钟无进展）。改用：
```bash
adb push app-debug.apk /data/local/tmp/r.apk
adb shell pm install -r -t /data/local/tmp/r.apk
```

## 未验证的部分

- **Android 4.4 真机**：Apple Silicon 跑不了 4.4 的模拟器镜像，能跑的只有 API 30+。
  minSdk 19 只经过 lint 静态确认（零 NewApi 问题）。
- **真遥控器键码**：模拟器发的是标准 D-pad 键码，真盒子可能有差异。
