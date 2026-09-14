# 网络收音机 · Web 预览版

Android 版（`../app/`，v1.0，兼容 Android 4.4+）的 **1:1 Web 复刻**，用来在打包 APK 之前
先验证交互和播放。纯静态页面，无构建步骤 —— 双击 `index.html` 就能跑。

## 跑起来

```bash
./serve.sh              # 本地：http://127.0.0.1:8099   ← 测试首选
./serve.sh --tunnel     # 额外开一条 cloudflared 公网隧道（地址每次重启都变）
```

**别用 `python -m http.server`** —— 它会让浏览器缓存旧代码，改动看不见。`serve.sh` 走的是
`devserver.py`（发 `no-store`），本地改动刷新即见。

## 文件

| 文件 | 对应 Android 版 |
|---|---|
| `index.html` | `res/layout/activity_main.xml` + `activity_settings.xml` + `activity_online.xml` |
| `style.css` | `res/values/styles.xml`（配色 #6D4C41 / #FFF8E1 / #C62828 原样照搬）|
| `stations.js` | `StationData.java`（22 个内置电台，2026-09-14 实测存活）|
| `app.js` | `RadioService.java` + `MainActivity.java` + `SettingsActivity.java` + `IcyMetadata.java` |
| `probe.js` | 无对应 —— 浏览器能力探针，仅开发期提示用 |
| `tvnav.js` | 无对应 —— 遥控器（D-pad）焦点导航，电视版专用 |
| `devserver.py` | 无对应 —— 开发用静态服务，比 `http.server` 多了 no-store（防缓存）|
| `verify-tunnel.js` | 无对应 —— 端到端验收探针，用无头 Chrome 真实播放一次（见下）|
| `verify-tv.js` | 无对应 —— 电视版验收：1920×1080 + 真·遥控器按键（见下）|
| `dbg-shot.js` | 无对应 —— 截电视/手机两种布局的图，改完界面拿它看一眼 |

## 电视版（安卓盒子 / 智能电视）

横屏大屏由 `@media (orientation: landscape) and (min-width: 900px)` 触发 ——
看的是**屏幕够不够宽、够不够横**，不是设备类型。所以电视、平板横放、桌面窗口
拉宽都走这一套，手机竖屏完全不受影响。

**布局**：左右两栏 —— 左边「收音机面板 + 播放/停止/收藏」，右边整块电台列表。

**字号**：按 3 米视距放大（电台名 46px、列表项 25px、按钮 78px 高）。

**遥控器**：这是电视上唯一能用的输入方式，而且浏览器**不提供**方向键焦点导航
（Chrome 内置的只有 Tab 键）。实测：在 1920×1080 下连按 10 次方向键，焦点纹丝不动。
所以 `tvnav.js` 自己实现了空间导航：

- **几何最近邻算法**：按方向筛选候选，用「轴向距离 + 垂直偏移 ×6」打分取最优。
  垂直权重是调过的 —— 权重太小会在列表可滚动时选中屏幕外的条目，焦点像是消失了。
- **确认键代理点击**：列表项是 `<li>` 不是 `<button>`，浏览器不会把 Enter 变成
  click，由 `tvnav.js` 手动补上。
- **弹层焦点陷阱**：弹层打开时焦点不会跑到背后的列表上。
- **焦点还原**：播放会让 `refreshUI()` 重建列表，原来的 `<li>` 被销毁、焦点掉回
  `body`（遥控器用户按一下 ↓ 就被甩到播放按钮上）。`render()` 现在会记住焦点
  在哪条电台、重建后按名字还原。

## 验收：链接到底通不通

"curl 拿到 200" 只证明静态文件在，不证明能出声。`verify-tunnel.js` 用无头 Chrome
真实加载页面、从列表点中电台、**确认 `currentTime` 真的在走** —— 出声与否只有这个说了算。

```bash
node verify-tunnel.js http://127.0.0.1:8099                              # 本地
node verify-tunnel.js https://xxx.trycloudflare.com                      # 公网
```

15 项断言，按页面协议自动走不同分支：

| 断言组 | 本地 http | 公网 https |
|---|---|---|
| 页面/脚本/列表渲染 | ✓ | ✓ |
| RTHK 真实播放（`currentTime` 推进） | ✓ | ✓ |
| 解析到 https 地址（无混合内容） | ✓ | ✓ |
| 纯 HTTP 台能播（AsiaFM） | ✓ 4.0s 出声 | — |
| 纯 HTTP 台被拦时给可照做提示 + 落 ERROR | — | ✓ |

**`app.js` 末尾有个 `__radiofm` 只读钩子**，就是为了这个探针。原因：`app.js` 整体是
IIFE，`audio`/`state`/`cur` 全是私有的，从 CDP 外部只能看到 DOM，而"有没有在出声"
恰恰是 DOM 上看不出来的（早期测试就因此误判过 —— 把设置面板里的 `<li>` 当成电台条目
点了，还报了"播放成功"）。钩子只读、`Object.freeze`、无写入口；打包 APK 时删掉即可。

> **踩坑记录**：`document.querySelectorAll('li')` 会连设置面板（定时唤醒/选台）里的
> `<li>` 一起选中，必须用 `document.getElementById('stationList').querySelectorAll('li')`
> 限定范围。
>
> **`[hidden]` 必须显式兜底**：UA 样式表里的 `[hidden]{display:none}` 优先级最低，
> 本项目 `.modal{display:flex}` 就把隐藏弹层重新显示了出来 —— 肉眼看不见（有遮罩盖着），
> 但占着 414×62 的真实尺寸，于是遥控器焦点会跑进隐藏弹层的按钮里。已在 style.css
> 开头加 `[hidden]{display:none!important}` 兜底。
>
> **`display:contents` 的两个副作用**（竖屏布局靠它保持原样，踩过两次）：
> 元素自身不产生盒子，它的 `flex`/`padding` 等属性一并失效；后代直接变成更外层
> 容器的 flex 项。所以 `.controls button.big{flex:none}` 必须写在 `button.big` **之后**
> （同等特异性下后出现的才生效），否则按钮会被拉成整屏高的色块。
>
> 隧道健康度看 `/tmp/radiofm-cf3.log` 里的 `Registered tunnel connection`；
> 若出现 `no more connections active and exiting` 说明隧道已死，但那**不代表域名失效** ——
> 重启后会拿到一个**新域名**，旧链接自然就打不开了。

### 电视版验收

```bash
node verify-tv.js                                       # 默认打本地，1920×1080
```

24 项断言，分四组：布局尺寸、遥控器导航（真按键）、滚动导航、竖屏回归。
关键是**必须用 `Input.dispatchKeyEvent` 发真按键** —— 用 `li.click()` 只是 JS 派发
一个 click 事件，压根不经过焦点，测不出导航有没有生效（早期就因此误判过）。

## 已对齐的功能

播放/暂停/停止 · 收藏（双击列表项切换）· 搜索过滤（名称/分类/地区）· 只看收藏 ·
自定义电台 · M3U/PLS 导入（粘贴或给地址）· 在线电台库（radio-browser.info，自动过滤 HLS）·
定时关闭（15/30/60/90 分，带倒计时）· 定时唤醒（每分钟检查）· ICY 曲目信息 · 断流重试

**重试逻辑与原生版一致**：同一地址重试 2 次（间隔 1.2s）→ 换下一个备用地址（间隔 0.8s）
→ 全部失败才进 ERROR 态。

## Web 与 Android 的差异（都不是 bug）

1. **浏览器不给自动播放。** 首次必须用户点一下播放键，之后换台才能无缝。
   页面被拦截时会提示「请再点一次播放键」。

2. **HTTPS 页面下，http:// 的流会被拦**（混合内容）。这是浏览器的硬规则，绕不过去。
   - 每条流按 `stations.js` 里**实测的 `httpsOk`** 决定候选地址：能走 https 就走 https；
     已知纯 HTTP 的（`httpsOk:false`）**直接跳过 https**，省掉 3.6 秒白试；
     未知的（用户自建）才先试 https 再回落
   - 确定性失败（https 页面遇到 http 源）**不重试**，直接报错并给出可行建议
   - 本地 `http://127.0.0.1` 打开时不受限，所有台都能播
   - 公网 https 下，纯 HTTP 的台（AsiaFM 全系 5 个、Lam Rim、Curiosity）列表里标
     `⚠ 仅 HTTP`，点了会明确提示，不会静默失败
   - **打包成 APK 后没有这个限制**（原生壳子不受同源策略约束）

3. **ICY 曲目信息公网下大概率取不到。** 浏览器读不到第三方流的响应头（拿不到
   `icy-metaint`），这是同源策略，不是代码问题。
   - 本机 localhost 直连：部分流放行 CORS，能拿到
   - 原生壳子里：无 CORS 限制，正常可用
   - 想在公网预览时也看到，可自建一个带 CORS 头的转发，然后
     用 `?icyproxy=https://your-proxy/?url=` 打开页面

4. **定时唤醒**在 Android 上是 AlarmManager（应用没开也能拉起）；web 上退化成
   「页面开着才生效」，每分钟检查一次。本来也只是预览用。

5. **屏幕常亮**用 Wake Lock API 替代 WakeLock 权限，只在播放时申请。

## 已知会失败的电台

见 `stations.js` 末尾的 `KNOWN_DEAD_NOTES`。主要是蜻蜓FM 全系（403 防盗链），
连带 CNR 中国之声、绝大多数省市台 —— 原生版也是同样的结论。

## iOS 注意

iPhone 的 Safari 对音频自动播放限制比 Android 严得多，纯网页形式很可能点了没反应。
`probe.js` 会主动弹提示引导「添加到主屏幕」。**这是开发期预览的固有限制，不是代码问题
—— 正式形态是 APK，跑在原生壳子（WebView）里不受此限。**
