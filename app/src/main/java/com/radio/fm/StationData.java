package com.radio.fm;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置电台表。
 *
 * 全部地址在 2026-09-14 实测过：HTTP 200 且能拉到真实音频数据（不是只连得上）。
 *
 * ── 关于蜻蜓FM（重要，别再重复踩坑）──
 * 曾一度判定「蜻蜓系全死」，那个结论是错的。蜻蜓有 **User-Agent 黑名单**：
 * 实测 curl / Mozilla / VLC / Stagefright / ExoPlayer 全部 200，
 * **只有 `WinampMPEG` 返回 403**。当初的探测脚本为了取 ICY 元数据特意带了
 * Winamp 的 UA，于是每个蜻蜓地址都被误判成失效。
 * Android MediaPlayer 的默认 UA 实测可以通过，本表大量使用蜻蜓地址。
 *
 * ── 关于 https/http 的先后顺序 ──
 * 蜻蜓系的台普遍能同时走 https 和 http（实测同路径换协议即可，两个域名
 * lhttp.qtfm.cn / lhttp.qingting.fm 更是解析到同一台服务器 123.60.16.94）。
 *
 * **大陆台一律 http 在前**，理由是实测出来的两条：
 *   1. 这些源只支持 TLS 1.2 —— 实测 TLS 1.0 直接握手失败（返回 000）。
 *      而 Android 4.4 的系统虽然支持 TLS 1.2，默认却只启用 TLS 1.0，
 *      MediaPlayer 走的又是 **native TLS 栈**，Tls12.java 那个
 *      HttpsURLConnection 补丁对它无效 → https 在 4.4 上必然先失败一次。
 *      本应用的主力机型就是 4.4，每条 https 都是一次白等的握手超时。
 *   2. 实测 2026-09-21：29 个双地址的台里 21 个 http 更快，https 快的
 *      那几个只快 0.01~0.10s（噪声级）。大陆台 http 快的幅度最大，
 *      中国之声 0.66s→0.35s、顺德音乐之声 0.41s→0.16s。
 * 大陆台是本应用的主力场景，所以它们 http 在前；新系统上 http 同样能播，
 * 不存在兼容性倒退。
 *
 * 港台/国外台保持 https 在前：那些源地理距离远，https 与否对延迟影响很小，
 * 而境外链路上 https 的完整性保护更有价值。
 *
 * ── 关于 HLS ──
 * HLS / .m3u8 流 **Android 4.4 的 MediaPlayer 不支持**（5.0+ 才支持）。
 *
 * **HLS 与否由 Station.isHls() 从地址推导，不靠 region 字段标记。**
 * 曾经用 region="安卓9" 兼职这件事，后来把 28 个省级卫视归到「中央」时
 * 标记没跟着走，那批台就在 4.4 的「全部频道」里露了出来、点了不出声。
 * 内容分类与技术属性必须是两个独立来源，一个字段不能兼两职。
 *
 * 4.4 上「全部频道」自动隐藏 HLS 台（MainActivity.matchesCategory），
 * 但进具体分类仍能看到它们 —— 免得"某些分类是空的"看起来像坏了。
 *
 * HLS 台的内容分布（2026-09-21 实测，归在「央广省级」分类里，region 一律「中央」）：
 *   · 央广官方 satellitepull.cnr.cn，13 个频道。**地址不能带 wsSession 参数**
 *     —— 那个 token 会过期（实测带 token 反而返回 400，去掉就 200）。
 *     裸地址会 302 到 CDN 并自签新 token，所以写裸地址就行。
 *   · CCTV-1~17 / CETV-1~4 + 各省卫视伴音（piccpndali.v.myalicdn.com），共 52 个
 *   · 中国国际广播电台 HIT FM
 *
 * ── 探测电台源时的坑（务必照做）──
 * 必须带 `Stagefright/1.2 (Linux;Android 4.4)` 这个 User-Agent ——
 * 那是 Android MediaPlayer 的真实 UA。用 curl 默认 UA 或 Mozilla 会得到
 * **假失败**：实测 zeno.fm 对默认 UA 返回 401，对 Stagefright 返回 200。
 * 这和当年「蜻蜓全系已死」的误判是同一类错误（详见下面对蜻蜓的说明）。
 *
 * 维护建议：失效地址用「更多 → 添加自定义电台」补，或改本文件重新编译。
 * 每条支持多个备用地址，前一个失败会自动试下一个。
 */
public final class StationData {

    private StationData() {}

    /** 2026-09-14 实测存活。备注里的数字是 radio-browser.info 的票数，仅作参考。 */
    public static List<Station> builtIn() {
        List<Station> list = new ArrayList<Station>();

        // ================= 大陆 · 中央台 =================
        list.add(new Station("中国之声 CNR-1", "新闻", "中央",
                "http://lhttp.qtfm.cn/live/15318317/64k.mp3",
                "https://lhttp.qtfm.cn/live/15318317/64k.mp3"));      // v=9932

        // ================= 大陆 · 北京 =================
        list.add(new Station("北京新闻广播", "新闻", "中央",
                "http://lhttp.qtfm.cn/live/339/64k.mp3",
                "https://lhttp.qtfm.cn/live/339/64k.mp3"));           // v=1198
        list.add(new Station("北京交通广播", "交通", "中央",
                "http://lhttp.qingting.fm/live/336/64k.mp3",
                "https://lhttp.qingting.fm/live/336/64k.mp3"));       // v=637
        list.add(new Station("北京文艺广播", "文艺", "中央",
                "http://lhttp.qtfm.cn/live/333/64k.mp3",
                "https://lhttp.qtfm.cn/live/333/64k.mp3"));           // v=539
        list.add(new Station("北京音乐广播", "音乐", "中央",
                "http://lhttp.qtfm.cn/live/332/64k.mp3",
                "https://lhttp.qtfm.cn/live/332/64k.mp3"));           // v=355

        // ================= 大陆 · 上海 =================
        list.add(new Station("上海新闻广播", "新闻", "中央",
                "http://lhttp.qingting.fm/live/270/64k.mp3"));
        list.add(new Station("上海东广新闻台", "新闻", "中央",
                "http://lhttp.qingting.fm/live/275/64k.mp3"));
        list.add(new Station("上海动感101", "流行", "中央",
                "http://lhttp.qingting.fm/live/274/64k.mp3",
                "https://lhttp.qingting.fm/live/274/64k.mp3"));       // v=774
        list.add(new Station("上海经典音乐广播", "经典", "中央",
                "http://lhttp.qingting.fm/live/267/64k.mp3"));
        list.add(new Station("上海音乐广播", "音乐", "中央",
                "http://lhttp.qingting.fm/live/273/64k.mp3"));

        // ================= 大陆 · 广东 =================
        list.add(new Station("广东新闻广播", "新闻", "中央",
                "http://lhttp.qtfm.cn/live/1254/64k.mp3",
                "https://lhttp.qtfm.cn/live/1254/64k.mp3"));          // v=1225
        list.add(new Station("广东珠江经济台", "经济", "中央",
                "http://lhttp.qtfm.cn/live/1259/64k.mp3",
                "https://lhttp.qtfm.cn/live/1259/64k.mp3"));          // v=1871
        list.add(new Station("广东音乐之声", "音乐", "中央",
                "http://lhttp.qtfm.cn/live/1260/64k.mp3",
                "https://lhttp.qtfm.cn/live/1260/64k.mp3"));          // v=1261
        list.add(new Station("广东交通之声", "交通", "中央",
                "http://lhttp.qtfm.cn/live/1262/64k.mp3",
                "https://lhttp.qtfm.cn/live/1262/64k.mp3"));          // v=992
        list.add(new Station("广东股市广播", "财经", "中央",
                "http://lhttp.qtfm.cn/live/4847/64k.mp3",
                "https://lhttp.qtfm.cn/live/4847/64k.mp3"));          // v=902
        list.add(new Station("广东城市之声", "综合", "中央",
                "http://lhttp.qtfm.cn/live/469/64k.mp3",
                "https://lhttp.qtfm.cn/live/469/64k.mp3"));           // v=515
        list.add(new Station("深圳新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/1270/64k.mp3"));
        list.add(new Station("广州金曲音乐广播", "音乐", "省市县",
                "http://lhttp.qingting.fm/live/20192/64k.mp3"));
        list.add(new Station("广州新闻资讯广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/4848/64k.mp3"));
        list.add(new Station("顺德音乐之声", "音乐", "省市县",
                "http://lhttp.qtfm.cn/live/20500150/64k.mp3",
                "https://lhttp.qtfm.cn/live/20500150/64k.mp3"));      // v=602

        // ================= 大陆 · 其他省市 =================
        list.add(new Station("四川新闻广播", "新闻", "中央",
                "http://lhttp.qtfm.cn/live/4906/64k.mp3",
                "https://lhttp.qtfm.cn/live/4906/64k.mp3"));          // v=421
        list.add(new Station("江苏经典流行音乐广播", "经典", "中央",
                "http://lhttp.qtfm.cn/live/4938/64k.mp3",
                "https://lhttp.qtfm.cn/live/4938/64k.mp3"));          // v=366
        list.add(new Station("河南星河音乐广播", "音乐", "中央",
                "http://lhttp.qingting.fm/live/20210755/64k.mp3"));
        list.add(new Station("郑州新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/1220/64k.mp3"));
        list.add(new Station("济南故事广播", "故事", "省市县",
                "http://lhttp.qtfm.cn/live/1672/64k.mp3",
                "https://lhttp.qtfm.cn/live/1672/64k.mp3"));          // v=687
        list.add(new Station("安徽小说评书广播", "评书", "中央",
                "http://lhttp.qtfm.cn/live/1951/64k.mp3",
                "https://lhttp.qtfm.cn/live/1951/64k.mp3"));          // v=1675
        list.add(new Station("长沙 BIG RADIO 流行音乐", "流行", "省市县",
                "http://lhttp.qingting.fm/live/20847/64k.mp3"));

        // ================= 大陆 · 网络台（非蜻蜓源）=================
        list.add(new Station("CityFM 城市音乐台", "音乐", "网络",
                "https://lhttp.qtfm.cn/live/20500153/64k.mp3",
                "http://lhttp.qtfm.cn/live/20500153/64k.mp3"));
        list.add(new Station("MY FM 全国音乐频道", "音乐", "网络",
                "http://lhttp.qingting.fm/live/20194/64k.mp3"));
        list.add(new Station("雨声轻音乐", "轻音乐", "网络",
                "https://stream.zeno.fm/689zc32y4x8uv",
                "http://stream.zeno.fm/689zc32y4x8uv"));
        list.add(new Station("德云社相声合集", "相声", "网络",
                "https://stream.zeno.fm/yqawwmweq8mtv",
                "http://stream.zeno.fm/yqawwmweq8mtv"));
        list.add(new Station("BBN 中文", "宗教", "网络",
                "https://streams.radiomast.io/ce298b32-8776-4192-9900-092f44b63e7f",
                "http://streams.radiomast.io/ce298b32-8776-4192-9900-092f44b63e7f"));

        // ================= 港台 =================
        list.add(new Station("香港电台 RTHK Radio 1", "综合", "香港",
                "http://stm.rthk.hk/radio1"));
        list.add(new Station("香港电台 RTHK Radio 2", "综合", "香港",
                "http://stm.rthk.hk/radio2"));
        list.add(new Station("香港电台 RTHK Radio 3", "英文", "香港",
                "http://stm.rthk.hk/radio3"));
        list.add(new Station("香港电台 RTHK Radio 4", "古典", "香港",
                "http://stm.rthk.hk/radio4"));
        list.add(new Station("香港电台 RTHK Radio 5", "粤语", "香港",
                "http://stm1.rthk.hk/radio5"));
        list.add(new Station("香港电台 RTHK 普通话台", "普通话", "香港",
                "http://stm.rthk.hk/radiopth"));
        list.add(new Station("香港国际机场塔台 VHHH", "航空", "香港",
                "https://s1-fmt2.liveatc.net/vhhh5",
                "http://s1-fmt2.liveatc.net/vhhh5"));
        list.add(new Station("AsiaFM 高清音乐台", "音乐", "网络",
                "http://asiafm.hk:8000/asiahd"));
        list.add(new Station("AsiaFM 亚洲经典台", "经典", "网络",
                "http://goldfm.cn:8000/goldfm"));
        list.add(new Station("AsiaFM 亚洲热歌台", "流行", "网络",
                "http://hot.asiafm.net:8000/asiafm"));
        list.add(new Station("AsiaFM 亚洲天空台", "流行", "网络",
                "http://funradio.cn:8000/funradio"));
        list.add(new Station("AsiaFM 亚洲粤语台", "粤语", "网络",
                "http://yyt.asiafm.net:8000/asiafm"));
        list.add(new Station("Anison 动漫音乐台", "动漫", "国际",
                "http://pool.anison.fm:9000/AniSonFM(320)"));
        list.add(new Station("Chinese Music World 华语音乐", "华语", "网络",
                "https://radio.chinesemusicworld.com/chinesemusic.mp3",
                "http://radio.chinesemusicworld.com/chinesemusic.mp3"));
        list.add(new Station("Acast 华语电台", "综合", "网络",
                "https://acast01.kolorboxlab.com/radio/8010/radio.mp3",
                "http://acast01.kolorboxlab.com/radio/8010/radio.mp3"));
        list.add(new Station("法国国际广播 RFI 中文", "新闻", "国际",
                "https://rfienchinois64k.ice.infomaniak.ch/rfienchinois-64.mp3",
                "http://rfienchinois64k.ice.infomaniak.ch/rfienchinois-64.mp3"));
        list.add(new Station("Fred Film Radio 中文", "影视", "国际",
                "https://s10.webradio-hosting.com/proxy/fredradiocn/stream",
                "http://s10.webradio-hosting.com/proxy/fredradiocn/stream"));
        list.add(new Station("Radio Maria Chinese", "宗教", "国际",
                "https://onair7.xdevel.com/proxy/xautocloud_nwct_1310?mp=/;",
                "http://onair7.xdevel.com/proxy/xautocloud_nwct_1310?mp=/;"));
        list.add(new Station("Lam Rim 藏传佛教电台", "宗教", "国际",
                "http://199.180.72.2:9097/lamrim"));
        list.add(new Station("Swiss News 瑞士新闻", "新闻", "国际",
                "https://replaynewszh.ice.infomaniak.ch/replaynewszh-128.mp3",
                "http://replaynewszh.ice.infomaniak.ch/replaynewszh-128.mp3"));
        list.add(new Station("Curiosity 电台", "综合", "国际",
                "http://curiosity.shoutca.st:8019/stream"));


        // ================= 蜻蜓地方台（341 个，全部实测 200 且为 MP3 —— 4.4 直接可播） =================
        list.add(new Station(" FM100.8 包河之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022668/64k.mp3"));
        list.add(new Station("AsiaFM安岳综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022417/64k.mp3"));
        list.add(new Station("FM101仙居融媒体广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021908/64k.mp3"));
        list.add(new Station("FM102.4 靖江广播电台（蜻蜓mp3线路）", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318120/64k.mp3"));
        list.add(new Station("FM104.1北岳之声 浑源人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212209/64k.mp3"));
        list.add(new Station("FM104.7 澧县人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318178/64k.mp3"));
        list.add(new Station("FM105平阳电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022624/64k.mp3"));
        list.add(new Station("FM98.3如皋人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207734/64k.mp3"));
        list.add(new Station("Nice Radio 永安广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318388/64k.mp3"));
        list.add(new Station("Radio Impetus 心动电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500161/64k.mp3"));
        list.add(new Station("VOK 库尔勒梨城之声 FM105.3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212425/64k.mp3"));
        list.add(new Station("万宁综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500237/64k.mp3"));
        list.add(new Station("万盛融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318480/64k.mp3"));
        list.add(new Station("三台人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318544/64k.mp3"));
        list.add(new Station("上海交通广播 FM105.7", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/266/64k.mp3"));
        list.add(new Station("上海戏剧曲艺广播 AM1197 FM97.2", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/269/64k.mp3"));
        list.add(new Station("上海第一财经广播 FM90.9", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/276/64k.mp3"));
        list.add(new Station("东海综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500220/64k.mp3"));
        list.add(new Station("东港新东港广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022186/64k.mp3"));
        list.add(new Station("东源电台 FM101.5", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500124/64k.mp3"));
        list.add(new Station("中国·汤阴 FM90.5", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500205/64k.mp3"));
        list.add(new Station("临沂音乐科教广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4017/64k.mp3"));
        list.add(new Station("丹阳市融媒体中心综合广播 FM97.9 丹阳之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207749/64k.mp3"));
        list.add(new Station("习水综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318201/64k.mp3"));
        list.add(new Station("云南交通之声", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1928/64k.mp3"));
        list.add(new Station("云霄综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500106/64k.mp3"));
        list.add(new Station("京哈高速沿线广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022520/64k.mp3"));
        list.add(new Station("仁寿人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021453/64k.mp3"));
        list.add(new Station("仙桃综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211562/64k.mp3"));
        list.add(new Station("佛冈电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318379/64k.mp3"));
        list.add(new Station("保山综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022446/64k.mp3"));
        list.add(new Station("健康广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20470/64k.mp3"));
        list.add(new Station("公主岭综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212386/64k.mp3"));
        list.add(new Station("兰考综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500167/64k.mp3"));
        list.add(new Station("兴义市广播电视台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500110/64k.mp3"));
        list.add(new Station("兴仁广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500077/64k.mp3"));
        list.add(new Station("兴宁电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500218/64k.mp3"));
        list.add(new Station("兵团二师铁门关综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211703/64k.mp3"));
        list.add(new Station("兵团八师石河子新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500118/64k.mp3"));
        list.add(new Station("兵团四师可克达拉综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500054/64k.mp3"));
        list.add(new Station("内蒙古交通之声", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1884/64k.mp3"));
        list.add(new Station("内蒙古农村牧区广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1888/64k.mp3"));
        list.add(new Station("内蒙古新闻综合广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1883/64k.mp3"));
        list.add(new Station("内蒙古蒙语广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1882/64k.mp3"));
        list.add(new Station("内蒙古音乐之声", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1886/64k.mp3"));
        list.add(new Station("凌源综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318298/64k.mp3"));
        list.add(new Station("利津县融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500138/64k.mp3"));
        list.add(new Station("动听913（宣化区融媒体中心综合广播）", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318538/64k.mp3"));
        list.add(new Station("动感调频FM94.3 沙湾人民广播电台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500064/64k.mp3"));
        list.add(new Station("包头城乡广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1892/64k.mp3"));
        list.add(new Station("北碚综合广播·重庆嘉陵之声FM88.7", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/20211692/64k.mp3"));
        list.add(new Station("北部湾之声 The Voice of Beibu Gulf", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1757/64k.mp3"));
        list.add(new Station("南京音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4963/64k.mp3"));
        list.add(new Station("南安市广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021731/64k.mp3"));
        list.add(new Station("南川融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318405/64k.mp3"));
        list.add(new Station("南平综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022065/64k.mp3"));
        list.add(new Station("卫辉综合广播 动听925", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500152/64k.mp3"));
        list.add(new Station("叙州综合广播 汽车音乐广播FM94.2", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500137/64k.mp3"));
        list.add(new Station("合肥交通信息广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1960/64k.mp3"));
        list.add(new Station("合肥文旅广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1961/64k.mp3"));
        list.add(new Station("合肥文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1975/64k.mp3"));
        list.add(new Station("吕梁交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4899/64k.mp3"));
        list.add(new Station("吕梁综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4020/64k.mp3"));
        list.add(new Station("周口交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318700/64k.mp3"));
        list.add(new Station("呼和浩特交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021545/64k.mp3"));
        list.add(new Station("呼和浩特综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021543/64k.mp3"));
        list.add(new Station("咸宁交通音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5068/64k.mp3"));
        list.add(new Station("咸宁综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5067/64k.mp3"));
        list.add(new Station("哈尔滨冰城融媒体电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212259/64k.mp3"));
        list.add(new Station("商丘综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022443/64k.mp3"));
        list.add(new Station("嘉兴交通经济广播 FM92.2", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1135/64k.mp3"));
        list.add(new Station("嘉兴对农广播 FM88.2", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1136/64k.mp3"));
        list.add(new Station("嘉兴综合广播 FN104.1", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1154/64k.mp3"));
        list.add(new Station("四平交通文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022465/64k.mp3"));
        list.add(new Station("四平综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318197/64k.mp3"));
        list.add(new Station("固安县融媒体中心综合广播 FM107.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500225/64k.mp3"));
        list.add(new Station("固安综合广播 1079音乐有话说", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211619/64k.mp3"));
        list.add(new Station("城阳综合广播 青岛广播爱车940", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022537/64k.mp3"));
        list.add(new Station("声音控电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318519/64k.mp3"));
        list.add(new Station("大兴安岭综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500057/64k.mp3"));
        list.add(new Station("大理市电台苍洱调频", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1940/64k.mp3"));
        list.add(new Station("大理综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207747/64k.mp3"));
        list.add(new Station("大足综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211676/64k.mp3"));
        list.add(new Station("大连交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/3997/64k.mp3"));
        list.add(new Station("大连少儿广播 FM106.7", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1084/64k.mp3"));
        list.add(new Station("大连普兰店广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212414/64k.mp3"));
        list.add(new Station("天津新闻广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/5022134/64k.mp3"));
        list.add(new Station("天津经济广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/15318227/64k.mp3"));
        list.add(new Station("天长综合频率", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4854/64k.mp3"));
        list.add(new Station("太仓综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207759/64k.mp3"));
        list.add(new Station("太原经济广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4018/64k.mp3"));
        list.add(new Station("太和广播电视台 FM104.3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318579/64k.mp3"));
        list.add(new Station("威海交通广播FM102.2 FM95.0 AM1557", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20671/64k.mp3"));
        list.add(new Station("威海综合广播FM105.1 FM107.3 AM1206", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20669/64k.mp3"));
        list.add(new Station("威海音乐广播FM90.7 FM88.3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318612/64k.mp3"));
        list.add(new Station("威远综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500102/64k.mp3"));
        list.add(new Station("孝感交通音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022063/64k.mp3"));
        list.add(new Station("孝感综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022064/64k.mp3"));
        list.add(new Station("孝昌964电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318546/64k.mp3"));
        list.add(new Station("宁海新闻综合广播 FM98.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022406/64k.mp3"));
        list.add(new Station("安康交通旅游音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021862/64k.mp3"));
        list.add(new Station("安阳县Top Radio 88.1", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20209339/64k.mp3"));
        list.add(new Station("安阳市广播电视台交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/2138/64k.mp3"));
        list.add(new Station("安阳综合新闻广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318224/64k.mp3"));
        list.add(new Station("安阳音乐生活广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/2123/64k.mp3"));
        list.add(new Station("定州融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211638/64k.mp3"));
        list.add(new Station("宝鸡交通旅游广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318128/64k.mp3"));
        list.add(new Station("宝鸡综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318125/64k.mp3"));
        list.add(new Station("宣城交通文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5023/64k.mp3"));
        list.add(new Station("富顺综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022355/64k.mp3"));
        list.add(new Station("尤溪综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022498/64k.mp3"));
        list.add(new Station("山东维语都市广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/20211680/64k.mp3"));
        list.add(new Station("岳阳县综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500129/64k.mp3"));
        list.add(new Station("岳阳经济广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022391/64k.mp3"));
        list.add(new Station("崇礼综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500100/64k.mp3"));
        list.add(new Station("巧家新闻综合广播 白鹤之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211704/64k.mp3"));
        list.add(new Station("巴音郭楞汉语综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022108/64k.mp3?"));
        list.add(new Station("常州音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/2799/64k.mp3"));
        list.add(new Station("常熟市融媒体中心综合广播 声动1008", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/2792/64k.mp3"));
        list.add(new Station("广西交通广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1758/64k.mp3"));
        list.add(new Station("广西教育广播 私家车930", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1756/64k.mp3"));
        list.add(new Station("广西文艺广播 FM950广西音乐台", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4875/64k.mp3"));
        list.add(new Station("广西经济广播 970女主播电台", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1754/64k.mp3"));
        list.add(new Station("广西综合广播 新闻910", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1753/64k.mp3"));
        list.add(new Station("广饶广播电视台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500036/64k.mp3"));
        list.add(new Station("庆云县融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022403/64k.mp3"));
        list.add(new Station("应城市融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500227/64k.mp3"));
        list.add(new Station("延吉交通之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318331/64k.mp3"));
        list.add(new Station("延边旅游广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022438/64k.mp3"));
        list.add(new Station("延边朝鲜语新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20324/64k.mp3"));
        list.add(new Station("开封综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022653/64k.mp3"));
        list.add(new Station("开平电台 飞扬956", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5037/64k.mp3"));
        list.add(new Station("张家口综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318285/64k.mp3"));
        list.add(new Station("彬州市人民广播电台 mp3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500035/64k.mp3"));
        list.add(new Station("徐州农村广播（蜻蜓FM）", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211623/64k.mp3"));
        list.add(new Station("德阳经济生活广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022110/64k.mp3"));
        list.add(new Station("德阳综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4987/64k.mp3"));
        list.add(new Station("怀化交通文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022070/64k.mp3"));
        list.add(new Station("怀化综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022069/64k.mp3"));
        list.add(new Station("怀远人民广播电台 FM95.1", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021993/64k.mp3"));
        list.add(new Station("成安综合广播久久金曲 FM99.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211694/64k.mp3"));
        list.add(new Station("成武综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211637/64k.mp3"));
        list.add(new Station("成都文化休闲广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4892/64k.mp3"));
        list.add(new Station("成都新闻广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4897/64k.mp3"));
        list.add(new Station("成都经济广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1121/64k.mp3"));
        list.add(new Station("扬州江都广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022636/64k.mp3"));
        list.add(new Station("扬州邗江广播FM96.7", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211632/64k.mp3"));
        list.add(new Station("抗大之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500096/64k.mp3"));
        list.add(new Station("抚州交通音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500015/64k.mp3"));
        list.add(new Station("抚州新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500226/64k.mp3"));
        list.add(new Station("拉萨综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022138/64k.mp3"));
        list.add(new Station("新余经济交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20093/64k.mp3"));
        list.add(new Station("新密综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500144/64k.mp3"));
        list.add(new Station("新疆汉语新闻广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1902/64k.mp3"));
        list.add(new Station("新都区广播电视台综合广播 新声905", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500221/64k.mp3"));
        list.add(new Station("新野综合广播 FM89.8", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500242/64k.mp3"));
        list.add(new Station("无棣广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022198/64k.mp3"));
        list.add(new Station("日照交通生活广播 RZBC-2", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4005/64k.mp3"));
        list.add(new Station("昆明老年广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1937/64k.mp3"));
        list.add(new Station("星空电台 STAR RADIO", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022379/64k.mp3"));
        list.add(new Station("曲阳融媒FM90.4", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500219/64k.mp3"));
        list.add(new Station("望城综合广播 长沙925电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022076/64k.mp3"));
        list.add(new Station("杭州城市资讯广播 FM90.7杭州潮流音乐电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318146/64k.mp3"));
        list.add(new Station("松原交通文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212256/64k.mp3"));
        list.add(new Station("松原新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5079/64k.mp3"));
        list.add(new Station("枣强综合广播 年代995", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500202/64k.mp3"));
        list.add(new Station("柳州交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20571/64k.mp3"));
        list.add(new Station("柳州综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/21043/64k.mp3"));
        list.add(new Station("株洲交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/3971/64k.mp3"));
        list.add(new Station("桂林旅游音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1760/64k.mp3"));
        list.add(new Station("桂林综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1759/64k.mp3"));
        list.add(new Station("桓仁电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022699/64k.mp3"));
        list.add(new Station("梁山广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500235/64k.mp3"));
        list.add(new Station("梁平融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211646/64k.mp3"));
        list.add(new Station("梅州综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1257/64k.mp3"));
        list.add(new Station("楚雄综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4030/64k.mp3"));
        list.add(new Station("武汉经济广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20200/64k.mp3"));
        list.add(new Station("毕节交通音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022712/64k.mp3"));
        list.add(new Station("江夏综合广播 魅力FM1064城市生活音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022716/64k.mp3"));
        list.add(new Station("江油综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20673/64k.mp3"));
        list.add(new Station("江门新会电台 mp3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5061/64k.mp3"));
        list.add(new Station("江门旅游之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1283/64k.mp3"));
        list.add(new Station("江门综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1282/64k.mp3"));
        list.add(new Station("江陵广播电视台 综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500203/64k.mp3"));
        list.add(new Station("沧州综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021901/64k.mp3"));
        list.add(new Station("泸州对农经济生活广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021565/64k.mp3"));
        list.add(new Station("泸州综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021557/64k.mp3"));
        list.add(new Station("泽州广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021761/64k.mp3"));
        list.add(new Station("洛阳文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211321/64k.mp3"));
        list.add(new Station("济宁生活广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4008/64k.mp3"));
        list.add(new Station("浦东综合广播 东上海之声FM106.5", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/21355/64k.mp3"));
        list.add(new Station("浦江人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021924/64k.mp3"));
        list.add(new Station("海口音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20010/64k.mp3"));
        list.add(new Station("海门新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022640/64k.mp3"));
        list.add(new Station("涞水县流行音乐广播999正青春", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211620/64k.mp3"));
        list.add(new Station("淄博综合广播（FM89）", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1678/64k.mp3"));
        list.add(new Station("淮北交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211647/64k.mp3"));
        list.add(new Station("淮安区综合广播 淮安经典992", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318398/64k.mp3"));
        list.add(new Station("淮阴区FM100.6淮安车生活", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021970/64k.mp3"));
        list.add(new Station("深州综合频率FM106.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500238/64k.mp3"));
        list.add(new Station("渭南交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022389/64k.mp3"));
        list.add(new Station("湖南音乐之声广播 芒果音乐台", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4979/64k.mp3"));
        list.add(new Station("湛江廉江广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211578/64k.mp3"));
        list.add(new Station("湛江综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20617/64k.mp3"));
        list.add(new Station("滨州交通音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20519/64k.mp3"));
        list.add(new Station("滨州文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/21341/64k.mp3"));
        list.add(new Station("滨州综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021395/64k.mp3"));
        list.add(new Station("漯河交通广播mp3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022452/64k.mp3"));
        list.add(new Station("漯河综合广播mp3", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022660/64k.mp3"));
        list.add(new Station("漳浦综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022658/64k.mp3"));
        list.add(new Station("潍坊音乐文旅广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318631/64k.mp3"));
        list.add(new Station("澄海电台FM100.5", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022439/64k.mp3"));
        list.add(new Station("澎湃907   江阴人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/2789/64k.mp3"));
        list.add(new Station("濉溪综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500240/64k.mp3"));
        list.add(new Station("濮阳县FM1053快乐调频", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20206/64k.mp3"));
        list.add(new Station("焦作新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022557/64k.mp3"));
        list.add(new Station("玉环综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212390/64k.mp3"));
        list.add(new Station("甘肃人民广播电台农村广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/3941/64k.mp3"));
        list.add(new Station("益阳交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318153/64k.mp3"));
        list.add(new Station("盐城交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20326/64k.mp3?"));
        list.add(new Station("盐城滨海广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207779/64k.mp3"));
        list.add(new Station("盱眙综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500051/64k.mp3"));
        list.add(new Station("眉山综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4027/64k.mp3"));
        list.add(new Station("石嘴山综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022563/64k.mp3"));
        list.add(new Station("磁县融媒综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500116/64k.mp3"));
        list.add(new Station("福建海峡之声", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1744/64k.mp3"));
        list.add(new Station("秦皇岛体育广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20835/64k.mp3"));
        list.add(new Station("第一师阿拉尔人民广播电台新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500209/64k.mp3"));
        list.add(new Station("红河综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4033/64k.mp3"));
        list.add(new Station("肇庆高新之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500213/64k.mp3"));
        list.add(new Station("肥乡广播电视台音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500104/64k.mp3"));
        list.add(new Station("自强之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021905/64k.mp3"));
        list.add(new Station("芜湖交通经济广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5027/64k.mp3"));
        list.add(new Station("芜湖综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5029/64k.mp3"));
        list.add(new Station("苍溪人民广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500228/64k.mp3"));
        list.add(new Station("荆门综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211577/64k.mp3"));
        list.add(new Station("蓬安综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500206/64k.mp3"));
        list.add(new Station("蓬莱电台仙境之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500112/64k.mp3"));
        list.add(new Station("蚌埠交通文艺广播 FM98.4", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4577/64k.mp3"));
        list.add(new Station("蚌埠综合广播 FM107.9 AM765", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20154/64k.mp3"));
        list.add(new Station("襄州综合广播 都市965汽车音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500108/64k.mp3"));
        list.add(new Station("诏安广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500186/64k.mp3"));
        list.add(new Station("贵港综合广播/贵港金曲1019/FM101.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20697/64k.mp3"));
        list.add(new Station("赣榆综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500216/64k.mp3"));
        list.add(new Station("赤峰交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1899/64k.mp3"));
        list.add(new Station("赤峰农村牧区广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1898/64k.mp3"));
        list.add(new Station("赤峰综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1896/64k.mp3"));
        list.add(new Station("赤峰蒙语广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1897/64k.mp3"));
        list.add(new Station("轮台之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500099/64k.mp3"));
        list.add(new Station("辉南综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211566/64k.mp3"));
        list.add(new Station("辽宁经典音乐广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/20021/64k.mp3"));
        list.add(new Station("辽宁经济广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/20019/64k.mp3"));
        list.add(new Station("运城文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1191/64k.mp3"));
        list.add(new Station("运城金荔枝经典流行音乐广播电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318194/64k.mp3"));
        list.add(new Station("通化交通文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500120/64k.mp3"));
        list.add(new Station("郏县综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022022/64k.mp3"));
        list.add(new Station("郑州经典音乐广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1223/64k.mp3"));
        list.add(new Station("郫都综合广播·川味965", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500159/64k.mp3"));
        list.add(new Station("金堂综合广播FM88.9 成都年代音乐怀旧好声音", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500160/64k.mp3"));
        list.add(new Station("铜山综合广播 徐州经典音乐FM942", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318160/64k.mp3"));
        list.add(new Station("铜陵交通生活广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/21305/64k.mp3"));
        list.add(new Station("镇江交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/3985/64k.mp3"));
        list.add(new Station("镇江文艺广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4605/64k.mp3"));
        list.add(new Station("镇江综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/3984/64k.mp3"));
        list.add(new Station("镇海104.7 Nice FM", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20033/64k.mp3"));
        list.add(new Station("镇雄新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20210752/64k.mp3"));
        list.add(new Station("长垣广播电视台综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318663/64k.mp3"));
        list.add(new Station("长江水上安全信息台·长江之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5021868/64k.mp3"));
        list.add(new Station("闽侯综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500236/64k.mp3"));
        list.add(new Station("阆中综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500020/64k.mp3"));
        list.add(new Station("阜宁县融媒体中心 新闻综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20207753/64k.mp3"));
        list.add(new Station("阿克苏市融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500041/64k.mp3"));
        list.add(new Station("阿克苏维语综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318551/64k.mp3"));
        list.add(new Station("阿坝安多藏语综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500234/64k.mp3"));
        list.add(new Station("阿基米德-健康电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500232/64k.mp3"));
        list.add(new Station("阿拉善汉语综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022521/64k.mp3?"));
        list.add(new Station("阿拉善蒙语综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022555/64k.mp3"));
        list.add(new Station("陕西经济广播·唐诗电台", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/1603/64k.mp3"));
        list.add(new Station("霸州市融媒体中心综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211658/64k.mp3"));
        list.add(new Station("青海交通音乐卫星广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/5009/64k.mp3"));
        list.add(new Station("青海新闻综合广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/20063/64k.mp3"));
        list.add(new Station("青海经济广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/5008/64k.mp3"));
        list.add(new Station("韩城综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318413/64k.mp3"));
        list.add(new Station("韶关综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022074/64k.mp3"));
        list.add(new Station("项城936 项城综艺交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318335/64k.mp3"));
        list.add(new Station("项城广播-国风国潮105.9", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20210757/64k.mp3"));
        list.add(new Station("颍上广播电视台FM96.2", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500039/64k.mp3"));
        list.add(new Station("驻马店综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022118/64k.mp3"));
        list.add(new Station("高安电台 最爱942", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500014/64k.mp3"));
        list.add(new Station("鸡西交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500087/64k.mp3"));
        list.add(new Station("鸡西综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500089/64k.mp3"));
        list.add(new Station("鹤壁交通广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022089/64k.mp3"));
        list.add(new Station("黄山旅游广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1969/64k.mp3"));
        list.add(new Station("黄山综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1968/64k.mp3"));
        list.add(new Station("黄岩电台", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022671/64k.mp3"));
        list.add(new Station("黄梅之声", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022280/64k.mp3"));
        list.add(new Station("黑龙江交通广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4973/64k.mp3"));
        list.add(new Station("黑龙江老年·少儿广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4972/64k.mp3"));
        list.add(new Station("黑龙江都市·女性广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4968/64k.mp3"));
        list.add(new Station("黑龙江高校广播", "HLS", "中央",
                "https://lhttp-hw.qtfm.cn/live/4976/64k.mp3"));
        list.add(new Station("龙岩综合广播", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20709/64k.mp3"));
        list.add(new Station("龙游人民广播电台FM95.4", "HLS", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318359/64k.mp3"));

        // ================= 央广官方源（HLS —— 需 Android 5+） =================
        list.add(new Station("CNR-1 中国之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxzgzs/playlist.m3u8"));
        list.add(new Station("CNR-2 经济之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxjjzs/playlist.m3u8"));
        list.add(new Station("CNR-3 音乐之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxyyzs/playlist.m3u8"));
        list.add(new Station("CNR-4 经典音乐广播", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxdszs/playlist.m3u8"));
        list.add(new Station("CNR-9 文艺之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxwyzs/playlist.m3u8"));
        list.add(new Station("CNR-10 老年之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxlnzs/playlist.m3u8"));
        list.add(new Station("CNR-15 中国交通广播", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxzgjtgb/playlist.m3u8"));
        list.add(new Station("CNR-16 中国乡村之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxxczs/playlist.m3u8"));
        list.add(new Station("CNR 藏语广播", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxzygb/playlist.m3u8"));
        list.add(new Station("CNR 维吾尔语广播", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxwygb/playlist.m3u8"));
        list.add(new Station("CNR 哈萨克语广播", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxhygb/playlist.m3u8"));
        list.add(new Station("CRI HIT FM", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxhitfm/playlist.m3u8"));

        // ================= CCTV / 卫视伴音（HLS —— 需 Android 5+） =================
        list.add(new Station("CCTV-10科教", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv10_2.m3u8"));
        list.add(new Station("CCTV-11戏曲", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv11_2.m3u8"));
        list.add(new Station("CCTV-12社会与法", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv12_2.m3u8"));
        list.add(new Station("CCTV-13 新闻", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv13_2.m3u8"));
        list.add(new Station("CCTV-15音乐", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv15_2.m3u8"));
        list.add(new Station("CCTV-1综合", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8"));
        list.add(new Station("CCTV-2财经", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv2_2.m3u8"));
        list.add(new Station("CCTV-3综艺", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv3_2.m3u8"));
        list.add(new Station("CCTV-4中文国际", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv4_2.m3u8"));
        list.add(new Station("CCTV-4中文国际欧洲", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctveurope_2.m3u8"));
        list.add(new Station("CCTV-4中文国际美洲", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctvamerica_2.m3u8"));
        list.add(new Station("CCTV-5+体育赛事", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv5plus_2.m3u8"));
        list.add(new Station("CCTV-5体育", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv5_2.m3u8"));
        list.add(new Station("CCTV-6电影频道", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv6_2.m3u8"));
        list.add(new Station("CCTV-7国防军事", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv7_2.m3u8"));
        list.add(new Station("CCTV-8电视剧", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv8_2.m3u8"));
        list.add(new Station("CCTV-9纪录", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv9_2.m3u8"));
        list.add(new Station("CCTV-少儿", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv14_2.m3u8"));
        list.add(new Station("CCTV农业节目", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv17_2.m3u8"));
        list.add(new Station("CCTV奥林匹克", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cctv16_2.m3u8"));
        list.add(new Station("CETV-1", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cetv1_2.m3u8"));
        list.add(new Station("CETV-2", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cetv2_2.m3u8"));
        list.add(new Station("CETV-3", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cetv3_2.m3u8"));
        list.add(new Station("CETV-4", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/cetv4_2.m3u8"));
        list.add(new Station("东方卫视", "HLS", "省市县",
                "https://piccpndali.v.myalicdn.com/audio/dongfang_2.m3u8"));
        list.add(new Station("云南卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/yunnan_2.m3u8"));
        list.add(new Station("北京卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/btv1_2.m3u8"));
        list.add(new Station("吉林卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/jilin_2.m3u8"));
        list.add(new Station("四川卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/sichuan_2.m3u8"));
        list.add(new Station("天津卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/tianjin_2.m3u8"));
        list.add(new Station("宁夏卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/ningxia_2.m3u8"));
        list.add(new Station("安徽卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/anhui_2.m3u8"));
        list.add(new Station("山东卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/shandong_2.m3u8"));
        list.add(new Station("山西卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/shan1xi_2.m3u8"));
        list.add(new Station("广东卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/guangdong_2.m3u8"));
        list.add(new Station("广西卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/guangxi_2.m3u8"));
        list.add(new Station("新疆卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/xinjiang_2.m3u8"));
        list.add(new Station("江西卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/jiangxi_2.m3u8"));
        list.add(new Station("河北卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/hebei_2.m3u8"));
        list.add(new Station("河南卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/henan_2.m3u8"));
        list.add(new Station("海南卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/travel_2.m3u8"));
        list.add(new Station("湖北卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/hubei_2.m3u8"));
        list.add(new Station("甘肃卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/gansu_2.m3u8"));
        list.add(new Station("福建东南卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/dongnan_2.m3u8"));
        list.add(new Station("西藏卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/xizang_2.m3u8"));
        list.add(new Station("贵州卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/guizhou_2.m3u8"));
        list.add(new Station("辽宁卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/liaoning_2.m3u8"));
        list.add(new Station("重庆卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/chongqing_2.m3u8"));
        list.add(new Station("陕西卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/shan3xi_2.m3u8"));
        list.add(new Station("青海卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/qinghai_2.m3u8"));
        list.add(new Station("黑龙江卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/heilongjiang_2.m3u8"));



        // ============================================================
        // 2026-09-23 批量补充 1004 个台（radio-browser.info 全量拉取）
        // 全部实测可播（Stagefright UA）；按省归组、组内按票数排序。
        // 其中 402 个是 HLS —— 4.4 上会自动隐藏（见类注释的 isHls 说明）。
        // ============================================================

        // ===== 浙江（72 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("浙江之声", "综合", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm88/128k.m3u8"));
        list.add(new Station("浙江民生资讯广播", "新闻", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm996/128k.m3u8"));
        list.add(new Station("杭州华语之声网络广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20505/64k.mp3"));
        list.add(new Station("湖州交通文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/2811/64k.mp3"));
        list.add(new Station("长兴人民广播电台·Up Radio FM97.3太湖之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022311/64k.mp3"));
        list.add(new Station("象山综合广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXxiangshanaud/128k.m3u8"));
        list.add(new Station("桐乡人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021791/64k.mp3"));
        list.add(new Station("诸暨人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022482/64k.mp3"));
        list.add(new Station("衢州新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/20444/64k.mp3"));
        list.add(new Station("衢州交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20442/64k.mp3"));
        list.add(new Station("绍兴交通广播", "交通", "省市县",
                "http://live.shaoxing.com.cn/audio/s10001-jt2/index.m3u8"));
        list.add(new Station("萧山综合广播", "综合", "省市县",
                "https://l.cztvcloud.com/channels/lantian/SXxiaoshanaud1/128k.m3u8"));
        list.add(new Station("FM97 舟山交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/1161/64k.mp3"));
        list.add(new Station("浙江之声中波", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wxzjzs/playlist.m3u8?wsSession=1c983a4091d3a5f8a963629b-174704991695690&wsIPSercert=cfb15583bad3e1e7c083a9a0d492bf48"));
        list.add(new Station("三门峡交通文艺广播", "交通", "省市县",
                "https://stream.zeno.fm/cmbjv6tml6fuv"));
        list.add(new Station("三门峡综合广播", "综合", "省市县",
                "https://stream.zeno.fm/b7y9bwk1x68uv"));
        list.add(new Station("上虞广播-2", "综合", "省市县",
                "https://l.cztvcloud.com/channels/lantian/SXshangyu2/720p.m3u8"));
        list.add(new Station("上虞广播-3", "综合", "省市县",
                "https://l.cztvcloud.com/channels/lantian/SXshangyuaud/128k.m3u8"));
        list.add(new Station("杭州交通经济广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1133/64k.mp3"));
        list.add(new Station("FM998 舟山新闻综合广播", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/1160/64k.mp3"));
        list.add(new Station("台州交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/1146/64k.mp3"));
        list.add(new Station("天台人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022200/64k.mp3"));
        list.add(new Station("上虞广播-1", "综合", "省市县",
                "https://l.cztvcloud.com/channels/lantian/SXshangyu1/720p.m3u8"));
        list.add(new Station("浙江城市之声", "综合", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm107/128k.m3u8"));
        list.add(new Station("余姚广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXyuyaoaud/128k.m3u8"));
        list.add(new Station("平湖广播 FM90.6（直播间视频流）", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXpinghu4/720p.m3u8"));
        list.add(new Station("丽水龙泉广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXlongquanaud/128k.m3u8"));
        list.add(new Station("兰溪人民广播电台", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXlanxiaud/128k.m3u8"));
        list.add(new Station("开化广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXkaihuaaud/128k.m3u8"));
        list.add(new Station("嵊州综合广播 FM100.3", "综合", "省市县",
                "https://l.cztvcloud.com/channels/lantian/SXshengzhouaud/128k.m3u8"));
        list.add(new Station("浙江交通之声", "交通", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm93/128k.m3u8"));
        list.add(new Station("浙江音乐调频", "文艺", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm968/128k.m3u8"));
        list.add(new Station("浙江新闻广播", "新闻", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm988/128k.m3u8"));
        list.add(new Station("台州音乐广播", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/1144/64k.mp3"));
        list.add(new Station("永康电台FM106.6", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022570/64k.mp3"));
        list.add(new Station("东阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/21181/64k.mp3"));
        list.add(new Station("温州经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/1157/64k.mp3"));
        list.add(new Station("宁波经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/1152/64k.mp3"));
        list.add(new Station("台州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1145/64k.mp3"));
        list.add(new Station("杭州市广播电视台西湖之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1163/64k.mp3"));
        list.add(new Station("嵊泗广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXshengsiaud/128k.m3u8"));
        list.add(new Station("萧山有线广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXxiaoshanaud2/128k.m3u8"));
        list.add(new Station("文成广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXwenchengaud/128k.m3u8"));
        list.add(new Station("慈溪人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5021401/64k.mp3"));
        list.add(new Station("永嘉综合广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXyongjiaaud/128k.m3u8"));
        list.add(new Station("浙江旅游之声", "综合", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm1045/128k.m3u8"));
        list.add(new Station("武义广播FM87.7", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXwuyiaud/128k.m3u8"));
        list.add(new Station("江山市广播电视台广播节目", "综合", "省市县",
                "http://play-sh.quklive.com/live/1621846880977914.m3u8"));
        list.add(new Station("绍兴戏曲广播", "文艺", "省市县",
                "http://live.shaoxing.com.cn/audio/s10001-xq3/index.m3u8"));
        list.add(new Station("洞头广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXdongtouaud/128k.m3u8"));
        list.add(new Station("新昌人民广播电台·天姥之声", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20212423/64k.mp3"));
        list.add(new Station("三门人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318638/64k.mp3"));
        list.add(new Station("临海人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022437/64k.mp3"));
        list.add(new Station("临安综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20005/64k.mp3"));
        list.add(new Station("乐清人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20204/64k.mp3"));
        list.add(new Station("湖州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/2810/64k.mp3"));
        list.add(new Station("绍兴综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5052/64k.mp3"));
        list.add(new Station("温州交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1156/64k.mp3"));
        list.add(new Station("海宁大潮之声🌊", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022556/64k.mp3"));
        list.add(new Station("浙江经济广播", "财经", "中央",
                "http://ali-m-l.cztv.com/channels/lantian/fm95/128k.m3u8"));
        list.add(new Station("苍南广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXcangnanaud/128k.m3u8"));
        list.add(new Station("诸暨综合广播", "综合", "省市县",
                "http://l.cztvcloud.com/channels/lantian/SXzhujiaud/128k.m3u8"));
        list.add(new Station("温州之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1155/64k.mp3"));
        list.add(new Station("温州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1155/64k.mp3"));
        list.add(new Station("湖州经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/2812/64k.mp3"));
        list.add(new Station("温州对农广播", "乡村", "省市县",
                "http://lhttp.qingting.fm/live/1158/64k.mp3"));
        list.add(new Station("温州私家车音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/1149/64k.mp3"));
        list.add(new Station("温岭1036·温岭人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/4567/64k.mp3"));
        list.add(new Station("温州经济生活广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/1157/64k.mp3"));
        list.add(new Station("温州音乐之声", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/1149/64k.mp3"));
        list.add(new Station("瑞安人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1143/64k.mp3"));

        // ===== 山东（65 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("经典105·山东经典音乐广播", "文艺", "中央",
                "http://audiolive302.iqilu.com/sdradioShenghuo/sdradio04/playlist.m3u8"));
        list.add(new Station("山东音乐广播", "文艺", "中央",
                "http://audiolive302.iqilu.com/sdradioYinyue/sdradio07/playlist.m3u8"));
        list.add(new Station("山东交通广播", "交通", "中央",
                "http://audiolive302.iqilu.com/sdradioJiaotong/sdradio05/playlist.m3u8"));
        list.add(new Station("青岛新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/1673/64k.mp3"));
        list.add(new Station("安丘924(FM92.4)", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20212216/64k.mp3"));
        list.add(new Station("济南音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/1671/64k.mp3"));
        list.add(new Station("青岛交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1676/64k.mp3"));
        list.add(new Station("烟台经典音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/20500097/64k.mp3"));
        list.add(new Station("山东体育休闲广播（2）", "体育", "中央",
                "http://audiolive302.iqilu.com/sdradioTiyu/sdradio08/playlist.m3u8"));
        list.add(new Station("济南经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/1668/64k.mp3"));
        list.add(new Station("济南新闻综合广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/1667/64k.mp3"));
        list.add(new Station("济南文艺广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1670/64k.mp3"));
        list.add(new Station("青岛老年广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/4956/64k.mp3"));
        list.add(new Station("临沂文艺广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/3995/64k.mp3"));
        list.add(new Station("青岛音乐•体育广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/1677/64k.mp3"));
        list.add(new Station("曹县人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022340/64k.mp3"));
        list.add(new Station("临沂综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/3992/64k.mp3"));
        list.add(new Station("青岛胶州广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211644/64k.mp3"));
        list.add(new Station("临沂交通旅游广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/3993/64k.mp3"));
        list.add(new Station("潍坊新闻广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/20320/64k.mp3"));
        list.add(new Station("临沂经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/3994/64k.mp3"));
        list.add(new Station("济宁交通文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20087/64k.mp3"));
        list.add(new Station("青岛经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/1674/64k.mp3"));
        list.add(new Station("济南交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/1669/64k.mp3"));
        list.add(new Station("山东文艺广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/20238/64k.mp3"));
        list.add(new Station("山东经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/20236/64k.mp3"));
        list.add(new Station("东营综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20144/64k.mp3"));
        list.add(new Station("即墨人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20807/64k.mp3"));
        list.add(new Station("东营交通音乐广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20142/64k.mp3"));
        list.add(new Station("临朐人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20500033/64k.mp3"));
        list.add(new Station("聊城经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/5022262/64k.mp3"));
        list.add(new Station("聊城综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022264/64k.mp3"));
        list.add(new Station("潍坊交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/4014/64k.mp3"));
        list.add(new Station("济南都市广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022333/64k.mp3"));
        list.add(new Station("潍坊经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/20839/64k.mp3"));
        list.add(new Station("东营生活广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211580/64k.mp3"));
        list.add(new Station("枣庄音乐·人文广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/1689/64k.mp3"));
        list.add(new Station("烟台交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1684/64k.mp3"));
        list.add(new Station("章丘广播电视台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20212207/64k.mp3"));
        list.add(new Station("蒙阴广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318571/64k.mp3"));
        list.add(new Station("枣庄综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1686/64k.mp3"));
        list.add(new Station("阳信广播电视台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021991/64k.mp3"));
        list.add(new Station("枣庄交通·文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1688/64k.mp3"));
        list.add(new Station("潍坊市潍城区 FM100.8城市之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20211696/64k.mp3"));
        list.add(new Station("烟台综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1682/64k.mp3"));
        list.add(new Station("济宁综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/4901/64k.mp3"));
        list.add(new Station("寿光广播电视台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500211/64k.mp3"));
        list.add(new Station("金乡综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500085/64k.mp3"));
        list.add(new Station("聊城交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022263/64k.mp3"));
        list.add(new Station("枣庄经济生活广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/1687/64k.mp3"));
        list.add(new Station("高密广播电视台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20212417/64k.mp3"));
        list.add(new Station("滕州广播电视台", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022611/64k.mp3"));
        list.add(new Station("邹平市融媒体中心综合广播", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022097/64k.mp3"));

        // ===== 江苏（62 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("江苏新闻广播", "新闻", "中央",
                "http://lzlive.vojs.cn/5Fo8mMX/92/live.m3u8?"));
        list.add(new Station("江苏故事广播", "文艺", "中央",
                "http://lzlive.vojs.cn/rWjyus9/92/live.m3u8?"));
        list.add(new Station("江苏交通广播网", "交通", "中央",
                "http://lzlive.vojs.cn/4TaHTeL/92/live.m3u8?"));
        list.add(new Station("苏州新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/2808/64k.mp3"));
        list.add(new Station("江苏音乐广播", "文艺", "中央",
                "http://lzlive.vojs.cn/jAmO6Ng/92/live.m3u8?"));
        list.add(new Station("苏州都市音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/2803/64k.mp3"));
        list.add(new Station("苏州戏曲广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/20211622/64k.mp3"));
        list.add(new Station("江苏新闻综合广播", "新闻", "中央",
                "http://lzlive.vojs.cn/rTyLc36/92/live.m3u8"));
        list.add(new Station("无锡音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/2779/64k.mp3"));
        list.add(new Station("江苏文艺广播", "综合", "中央",
                "http://lzlive.vojs.cn/pL6NkZo/92/live.m3u8?"));
        list.add(new Station("苏州儿童广播", "少儿", "省市县",
                "http://lhttp.qingting.fm/live/2807/64k.mp3"));
        list.add(new Station("江苏金陵之声", "综合", "中央",
                "http://lzlive.vojs.cn/Hd2hIgM/92/live.m3u8?"));
        list.add(new Station("徐州音乐广播", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/4923/64k.mp3"));
        list.add(new Station("苏州交通经济广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/2806/64k.mp3"));
        list.add(new Station("江苏财经广播", "财经", "中央",
                "http://lzlive.vojs.cn/caijing/92/live.m3u8?"));
        list.add(new Station("苏州生活广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/2801/64k.mp3"));
        list.add(new Station("徐州新闻综合广播", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/4922/64k.mp3"));
        list.add(new Station("扬州新闻广播", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/5000/64k.mp3"));
        list.add(new Station("常州新闻综合广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/2798/64k.mp3"));
        list.add(new Station("盐城音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/5022380/64k.mp3"));
        list.add(new Station("无锡经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/2778/64k.mp3"));
        list.add(new Station("无锡交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/2780/64k.mp3"));
        list.add(new Station("徐州交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/4924/64k.mp3"));
        list.add(new Station("无锡新闻综合广播FM93.7", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/2777/64k.mp3"));
        list.add(new Station("无锡梁溪之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/2782/64k.mp3"));
        list.add(new Station("江苏健康广播", "综合", "中央",
                "http://lzlive.vojs.cn/EWpb5Ov/92/live.m3u8?"));
        list.add(new Station("南通交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5021533/64k.mp3"));
        list.add(new Station("常州交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/2796/64k.mp3"));
        list.add(new Station("江宁人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20500075/64k.mp3"));
        list.add(new Station("宿迁交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5004/64k.mp3"));
        list.add(new Station("南通综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/21277/64k.mp3"));
        list.add(new Station("常州经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/2794/64k.mp3"));
        list.add(new Station("淮安农村广播", "乡村", "省市县",
                "https://lhttp.qingting.fm/live/4588/64k.mp3"));
        list.add(new Station("淮安综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/4589/64k.mp3"));
        list.add(new Station("扬州经济音乐广播", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/2805/64k.mp3"));
        list.add(new Station("邳州人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/2809/64k.mp3"));
        list.add(new Station("扬州交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/2804/64k.mp3"));
        list.add(new Station("宜兴交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/3982/64k.mp3"));
        list.add(new Station("淮安经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/4587/64k.mp3"));
        list.add(new Station("如东综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20579/64k.mp3"));
        list.add(new Station("张家港综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021877/64k.mp3"));
        list.add(new Station("盐城农村广播·经典882", "乡村", "省市县",
                "https://lhttp.qtfm.cn/live/20332/64k.mp3"));
        list.add(new Station("盐城综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20330/64k.mp3"));
        list.add(new Station("宿迁新农村广播", "乡村", "省市县",
                "https://lhttp.qingting.fm/live/21265/64k.mp3"));
        list.add(new Station("仪征人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/15318182/64k.mp3"));
        list.add(new Station("吴江综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022050/64k.mp3"));
        list.add(new Station("幸福105 徐州农村广播", "乡村", "省市县",
                "http://stream3.huaihai.tv/aac_gbfm105/playlist.m3u8"));
        list.add(new Station("南通生活广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/21275/64k.mp3"));
        list.add(new Station("南通经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/21327/64k.mp3"));
        list.add(new Station("金湖综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/15318464/64k.mp3"));
        list.add(new Station("昆山人民广播电台", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500128/64k.mp3"));
        list.add(new Station("沛县综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500173/64k.mp3"));
        list.add(new Station("连云港交通广播", "交通", "省市县",
                "https://livenew.lyg1.com/JTGB927/playlist.m3u8"));
        list.add(new Station("连云港新农村广播", "乡村", "省市县",
                "https://livenew.lyg1.com/FM/playlist.m3u8"));
        list.add(new Station("连云港综合广播", "综合", "省市县",
                "https://livenew.lyg1.com/LYGZHGB/playlist.m3u8"));
        list.add(new Station("睢宁综合广播", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500191/64k.mp3"));

        // ===== 河北（59 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("河北新闻广播", "新闻", "中央",
                "https://radio.pull.hebtv.com/live/hebxw.m3u8"));
        list.add(new Station("河北音乐广播", "文艺", "中央",
                "https://radio.pull.hebtv.com/live/hebyy.m3u8"));
        list.add(new Station("河北交通广播", "交通", "中央",
                "https://radio.pull.hebtv.com/live/hebjt.m3u8"));
        list.add(new Station("衡水交通评书广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5021940/64k.mp3"));
        list.add(new Station("河北经济广播（故事广播）", "文艺", "中央",
                "https://radio.pull.hebtv.com/live/hebjj.m3u8"));
        list.add(new Station("涿州综合广播年代音乐959", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/5021381/64k.mp3"));
        list.add(new Station("沧州长书文艺广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5021902/64k.mp3"));
        list.add(new Station("河北旅游广播Hopei Travel Radio", "综合", "中央",
                "http://ls.qingting.fm/live/1651.m3u8"));
        list.add(new Station("河北文艺广播", "综合", "中央",
                "https://radio.pull.hebtv.com/live/hebwy.m3u8"));
        list.add(new Station("石家庄交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1655/64k.mp3"));
        list.add(new Station("石家庄音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/1654/64k.mp3"));
        list.add(new Station("廊坊综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/3948/64k.mp3"));
        list.add(new Station("廊坊交通长书广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/3948/64k.mp3"));
        list.add(new Station("河北综合广播", "综合", "中央",
                "https://radio.pull.hebtv.com/live/hebzh.m3u8"));
        list.add(new Station("河北私家车广播", "综合", "中央",
                "http://ls.qingting.fm/live/4868.m3u8"));
        list.add(new Station("石家庄综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1652/64k.mp3"));
        list.add(new Station("廊坊戏曲广播·飞扬105", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/20211678/64k.mp3"));
        list.add(new Station("河北农民广播", "综合", "中央",
                "https://radio.pull.hebtv.com/live/hebnczx.m3u8"));
        list.add(new Station("河北旅游文化广播", "综合", "中央",
                "https://radio.pull.hebtv.com/live/hebly.m3u8"));
        list.add(new Station("张家口986音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/5021801/64k.mp3"));
        list.add(new Station("唐山音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/4871/64k.mp3"));
        list.add(new Station("河北生活广播", "综合", "中央",
                "https://radio.pull.hebtv.com/live/hebshzx.m3u8"));
        list.add(new Station("唐山曹妃甸之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1660/64k.mp3"));
        list.add(new Station("邯郸交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/3950/64k.mp3"));
        list.add(new Station("高阳人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021555/64k.mp3"));
        list.add(new Station("沧州交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/3954/64k.mp3"));
        list.add(new Station("孟村回族自治县人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021914/64k.mp3"));
        list.add(new Station("邯郸经济文艺广播", "财经", "省市县",
                "https://lhttp.qingting.fm/live/4601/64k.mp3"));
        list.add(new Station("邢台新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/20211628/64k.mp3"));
        list.add(new Station("邢台交通•音乐广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/15318481/64k.mp3"));
        list.add(new Station("怀来人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022643/64k.mp3"));
        list.add(new Station("承德交通文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/15318216/64k.mp3"));
        list.add(new Station("衡水文艺广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021857/64k.mp3"));
        list.add(new Station("邯郸交通广播", "交通", "省市县",
                "http://ls.qingting.fm/live/3950.m3u8"));
        list.add(new Station("邢台生活广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318265/64k.mp3"));
        list.add(new Station("辛集综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021959/64k.mp3"));
        list.add(new Station("唐山交通文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1659/64k.mp3"));
        list.add(new Station("邯郸音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/4601/64k.mp3"));
        list.add(new Station("邯郸都市生活广播", "综合", "省市县",
                "http://ls.qingting.fm/live/3951.m3u8"));
        list.add(new Station("秦皇岛交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20849/64k.mp3"));
        list.add(new Station("唐山经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/15318431/64k.mp3"));
        list.add(new Station("张家口综艺广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021910/64k.mp3"));
        list.add(new Station("秦皇岛综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20855/64k.mp3"));
        list.add(new Station("邯郸新闻综合广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/5072/64k.mp3"));
        list.add(new Station("唐山综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1657/64k.mp3"));
        list.add(new Station("承德综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500052/64k.mp3"));
        list.add(new Station("张家口农业经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/5021507/64k.mp3"));
        list.add(new Station("张家口旅游广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021507/64k.mp3"));
        list.add(new Station("衡水综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022040/64k.mp3"));
        list.add(new Station("承德旅游生活广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318158/64k.mp3"));
        list.add(new Station("秦皇岛旅游经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/20859/64k.mp3"));
        list.add(new Station("邢台爱在104（沙河市融媒体中心）", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/5022484/64k.mp3"));
        list.add(new Station("周村区广播电视台", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500132/64k.mp3"));

        // ===== 河南（51 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("河南戏曲广播", "文艺", "中央",
                "https://stream.hndt.com/live/yule/playlist.m3u8"));
        list.add(new Station("河南交通广播", "交通", "中央",
                "https://stream.hndt.com/live/jiaotong/playlist.m3u8"));
        list.add(new Station("河南音乐广播", "文艺", "中央",
                "https://stream.hndt.com/live/yinyue/playlist.m3u8"));
        list.add(new Station("河南新闻广播", "新闻", "中央",
                "https://stream.hndt.com/live/xinwen/playlist.m3u8"));
        list.add(new Station("河南卷卷猫电台", "综合", "中央",
                "http://lhttp.qingting.fm/live/20500038/64k.mp3"));
        list.add(new Station("河南网络广播·摇滚天空台", "综合", "中央",
                "http://stream3.hndt.com/now/SXJtR4M4/playlist.m3u8"));
        list.add(new Station("郑州人民广播电台 音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/4921/64k.mp3"));
        list.add(new Station("郑州音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/4921/64k.mp3"));
        list.add(new Station("河南影视广播", "综合", "中央",
                "https://stream.hndt.com/live/yingshi/playlist.m3u8"));
        list.add(new Station("河南教育广播", "综合", "中央",
                "https://stream.hndt.com/live/jiaoyu/playlist.m3u8"));
        list.add(new Station("开封音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/4569/64k.mp3"));
        list.add(new Station("河南网络广播·爵士FM", "综合", "中央",
                "http://stream3.hndt.com/now/p16uWRAi/playlist.m3u8"));
        list.add(new Station("郑州交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1211/64k.mp3"));
        list.add(new Station("河南旅游广播", "综合", "中央",
                "https://stream.hndt.com/live/sijiache/playlist.m3u8"));
        list.add(new Station("河南信息广播", "综合", "中央",
                "https://stream.hndt.com/live/leling/playlist.m3u8"));
        list.add(new Station("河南网络广播·安全百科网络台", "综合", "中央",
                "http://stream3.hndt.com/now/4pcovD2L/playlist.m3u8"));
        list.add(new Station("河南卷卷猫电台（2）", "综合", "中央",
                "http://stream3.hndt.com/now/PHucVOu2/playlist.m3u8"));
        list.add(new Station("光山人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20500029/64k.mp3"));
        list.add(new Station("郑州人民广播电台 交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1211/64k.mp3"));
        list.add(new Station("郑州人民广播电台 文化娱乐广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1222/64k.mp3"));
        list.add(new Station("河南警广之声", "综合", "中央",
                "http://stream3.hndt.com/now/prIgXGFo/playlist.m3u8"));
        list.add(new Station("河南少儿广播", "少儿", "中央",
                "https://lhttp.qtfm.cn/live/20500038/64k.mp3"));
        list.add(new Station("郑州人民广播电台 经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/1221/64k.mp3"));
        list.add(new Station("洛阳音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/1226/64k.mp3"));
        list.add(new Station("开封交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1214/64k.mp3"));
        list.add(new Station("许昌综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022092/64k.mp3"));
        list.add(new Station("河南农村广播", "乡村", "中央",
                "https://stream.hndt.com/live/nongcun/playlist.m3u8"));
        list.add(new Station("河南戏曲网络广播", "文艺", "中央",
                "http://stream.hndt.com/live/wangluoxiqu/playlist.m3u8"));
        list.add(new Station("南乐县广播节目", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500136/64k.mp3"));
        list.add(new Station("郑州经济生活广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/1221/64k.mp3"));
        list.add(new Station("新乡交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1229/64k.mp3"));
        list.add(new Station("鹤壁综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022055/64k.mp3"));
        list.add(new Station("辉县人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20207782/64k.mp3"));
        list.add(new Station("新乡综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1228/64k.mp3"));
        list.add(new Station("洛阳交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1227/64k.mp3"));
        list.add(new Station("濮阳交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1233/64k.mp3"));
        list.add(new Station("获嘉人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021919/64k.mp3"));
        list.add(new Station("南阳交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/1212/64k.mp3"));
        list.add(new Station("平顶山综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022420/64k.mp3"));
        list.add(new Station("郑州文体旅游广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1222/64k.mp3"));
        list.add(new Station("洛阳综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1225/64k.mp3"));
        list.add(new Station("河南经济广播", "财经", "中央",
                "https://stream.hndt.com/live/jingji/playlist.m3u8"));
        list.add(new Station("许昌交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/5022095/64k.mp3"));
        list.add(new Station("濮阳综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20207739/64k.mp3"));
        list.add(new Station("平顶山交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022421/64k.mp3"));
        list.add(new Station("邓州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500198/64k.mp3"));
        list.add(new Station("登封综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022077/64k.mp3"));
        list.add(new Station("周口综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20212215/64k.mp3"));
        list.add(new Station("商丘交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5021932/64k.mp3"));
        list.add(new Station("清丰融媒广播 濮阳经典调频1038", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5021461/64k.mp3"));

        // ===== 广东（51 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("怀集音乐之声", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/4804/64k.mp3"));
        list.add(new Station("广东南粤之声", "综合", "中央",
                "https://lhttp.qtfm.cn/live/470/64k.mp3"));
        list.add(new Station("鹤山人民广播电台", "综合", "省市县",
                "http://ls.qingting.fm/live/1286.m3u8"));
        list.add(new Station("深圳生活广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1273/64k.mp3"));
        list.add(new Station("广州市番禺区广播电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20212427/64k.mp3"));
        list.add(new Station("中山环保旅游之声·快乐888", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1278/64k.mp3"));
        list.add(new Station("中山综合广播·新锐967", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1277/64k.mp3"));
        list.add(new Station("深圳综艺广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1271/64k.mp3"));
        list.add(new Station("普宁人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022527/64k.mp3"));
        list.add(new Station("花都人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1263/64k.mp3"));
        list.add(new Station("珠海交通875·环保经济广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1275/64k.mp3"));
        list.add(new Station("增城人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211702/64k.mp3"));
        list.add(new Station("广东珠江之声", "综合", "中央",
                "http://ls.qingting.fm/live/470.m3u8"));
        list.add(new Station("佛山南海广播", "综合", "省市县",
                "https://radiopull.radiofoshan.com.cn/live/1400820947_BSID_42_audio.m3u8"));
        list.add(new Station("珠海先锋951·综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1274/64k.mp3"));
        list.add(new Station("斗门人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318432/64k.mp3"));
        list.add(new Station("英德综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022392/64k.mp3"));
        list.add(new Station("惠州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5016/64k.mp3"));
        list.add(new Station("新兴电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20211602/64k.mp3"));
        list.add(new Station("云浮综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022442/64k.mp3"));
        list.add(new Station("恩平人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20701/64k.mp3"));
        list.add(new Station("佛山综合广播", "综合", "省市县",
                "https://radiopull.radiofoshan.com.cn/live/1400820947_BSID_46_audio.m3u8"));
        list.add(new Station("清远综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318668/64k.mp3"));
        list.add(new Station("茂名综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20500088/64k.mp3"));
        list.add(new Station("梅县客都之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021942/64k.mp3"));
        list.add(new Station("河源旅游广播", "综合", "省市县",
                "http://tmpstream.hyrtv.cn/lygb/sd/live.m3u8"));
        list.add(new Station("汕头综合广播", "综合", "省市县",
                "https://stream.zeno.fm/fjsl1teq6vjuv"));
        list.add(new Station("潮州戏曲广播", "文艺", "省市县",
                "http://ls.qingting.fm/live/4595.m3u8"));
        list.add(new Station("阳江综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318429/64k.mp3"));
        list.add(new Station("湾区音乐台_鹤山音乐广播", "文艺", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500163/64k.mp3"));
        list.add(new Station("河源综合广播", "综合", "省市县",
                "https://tmpstream.hyrtv.cn/zhgb/playlist.m3u8"));
        list.add(new Station("潮州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/4596/64k.mp3"));
        list.add(new Station("化州融媒体中心综合广播", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318689/64k.mp3"));
        list.add(new Station("广州经济交通广播电台", "交通", "省市县",
                "http://lhttp.qingting.fm/live/4955/64k.mp3"));
        list.add(new Station("广东文体广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/471/64k.mp3"));
        list.add(new Station("佛山顺德广播", "综合", "省市县",
                "https://radiopull.radiofoshan.com.cn/live/1400820947_BSID_44_audio.m3u8"));
        list.add(new Station("佛山电台FM94.6真爱946", "综合", "省市县",
                "https://radiopull.radiofoshan.com.cn/live/1400389414_BSID_42_audio.m3u8"));
        list.add(new Station("深圳飞扬971 FM97.1", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1271/64k.mp3"));
        list.add(new Station("深圳私家车广播 FM94.2", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1273/64k.mp3"));
        list.add(new Station("深圳交通广播 FM106.2", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1286/64k.mp3"));

        // ===== 四川（46 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("四川交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/4886/64k.mp3"));
        list.add(new Station("四川音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1110/64k.mp3"));
        list.add(new Station("AsiaFM郫都区综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/4581/64k.mp3?"));
        list.add(new Station("郫都综合广播亚洲音乐965", "文艺", "省市县",
                "http://asiafm.vip:8000/fm965"));
        list.add(new Station("金堂人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211686/64k.mp3"));
        list.add(new Station("中江综合广播年代音乐994", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/20500148/64k.mp3"));
        list.add(new Station("乐山音乐交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/4864/64k.mp3"));
        list.add(new Station("广汉人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20212405/64k.mp3"));
        list.add(new Station("西昌人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211706/64k.mp3"));
        list.add(new Station("四川民族广播", "民族", "中央",
                "https://lhttp.qtfm.cn/live/1115/64k.mp3"));
        list.add(new Station("达州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022394/64k.mp3"));
        list.add(new Station("达州交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022395/64k.mp3"));
        list.add(new Station("南部新闻综合·南部人民广播电台", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/20525/64k.mp3"));
        list.add(new Station("双流人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20211587/64k.mp3"));
        list.add(new Station("巴中交通旅游广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20209340/64k.mp3"));
        list.add(new Station("崇州人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20500066/64k.mp3"));
        list.add(new Station("自贡综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20207784/64k.mp3"));
        list.add(new Station("巴中综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20210239/64k.mp3"));
        list.add(new Station("攀枝花交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/4905/64k.mp3"));
        list.add(new Station("内江交通广播", "交通", "省市县",
                "https://www.scnj.tv/hls_njtv/live/jtgb.m3u8"));
        list.add(new Station("自贡文化旅游广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20529/64k.mp3"));
        list.add(new Station("攀枝花综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/4904/64k.mp3"));
        list.add(new Station("绵阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/4024/64k.mp3"));
        list.add(new Station("绵阳交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/4026/64k.mp3"));
        list.add(new Station("遂宁综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20182/64k.mp3"));
        list.add(new Station("内江综合广播", "综合", "省市县",
                "https://www.scnj.tv/hls_njtv/live/zhgb.m3u8"));
        list.add(new Station("南部交通音乐·南部人民广播电台", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5022604/64k.mp3"));
        list.add(new Station("眉山交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20207781/64k.mp3"));
        list.add(new Station("遂宁交通旅游广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20180/64k.mp3"));
        list.add(new Station("乐山综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1122/64k.mp3"));
        list.add(new Station("绵竹综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/15318098/64k.mp3"));
        list.add(new Station("雅安广播电视台综合广播", "综合", "省市县",
                "http://iovliveplay.radio.cn/fm/1600000003331h.m3u8"));
        list.add(new Station("龙泉驿区龙泉人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20207769/64k.mp3?app_id=web"));
        list.add(new Station("广元综合广播", "综合", "省市县",
                "https://m3u8.channel.dzsm.com/nmip-media/audiolive/audio101953/playlist.m3u8"));
        list.add(new Station("广安综合广播", "综合", "省市县",
                "https://live1.gatv.com.cn:90/live/xwgb.m3u8"));
        list.add(new Station("广元旅游广播", "综合", "省市县",
                "https://m3u8.channel.dzsm.com/nmip-media/audiolive/audio101760/playlist.m3u8"));
        list.add(new Station("广安交通旅游广播", "交通", "省市县",
                "https://live1.gatv.com.cn:90/live/LYJT.m3u8"));
        list.add(new Station("资阳综合广播", "综合", "省市县",
                "https://zbzy.zyrb.com.cn/sclivedt/zydt.m3u8"));
        list.add(new Station("大竹综合广播", "综合", "省市县",
                "https://lmt.dazhutv.cn/live3/live3.m3u8"));
        list.add(new Station("黑水综合广播·猛河之声", "综合", "省市县",
                "http://live.schstv.com:90/live/mhzs.m3u8"));
        list.add(new Station("雅安综合广播", "综合", "省市县",
                "https://play.yunxya.com/audiolive/1047.m3u8"));
        list.add(new Station("雅安交通旅游广播", "交通", "省市县",
                "https://play.yunxya.com/audiolive/whsh.m3u8"));
        list.add(new Station("渠县新闻综合广播", "新闻", "省市县",
                "http://183.222.248.153:81/hls/nu6vbbdw.m3u8"));

        // ===== 吉林（46 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("MY FM全国音乐频道——FM106.3  长春少儿与老年生活广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/4984/64k.mp3"));
        list.add(new Station("长春经济广播U FM88.0", "财经", "省市县",
                "http://lhttp.qingting.fm/live/4850/64k.mp3"));
        list.add(new Station("长春新闻综合广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/5013/64k.mp3"));
        list.add(new Station("长春交通之声", "交通", "省市县",
                "http://lhttp.qingting.fm/live/4967/64k.mp3"));
        list.add(new Station("吉林市音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/20211679/64k.mp3"));
        list.add(new Station("吉林市广播电视台经济广播", "财经", "中央",
                "http://lhttp.qingting.fm/live/1823/64k.mp3"));
        list.add(new Station("梅河口人民广播电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500115/64k.mp3"));
        list.add(new Station("吉林音乐广播", "文艺", "中央",
                "http://stream10.jlntv.cn/fm927/playlist.m3u8"));
        list.add(new Station("吉林旅游广播(2)", "综合", "中央",
                "https://lhttp.qingting.fm/live/20487/64k.mp3"));
        list.add(new Station("吉林市交通广播", "交通", "中央",
                "http://lhttp.qingting.fm/live/1819/64k.mp3"));
        list.add(new Station("永吉县魅力FM1008", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021975/64k.mp3"));
        list.add(new Station("靖宇县综合广播", "综合", "省市县",
                "http://stream9.jlntv.cn/jygb/playlist.m3u8"));
        list.add(new Station("白山交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5083/64k.mp3"));
        list.add(new Station("长春经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/4850/64k.mp3"));
        list.add(new Station("东丰综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/dfgb/playlist.m3u8"));
        list.add(new Station("桦甸综合广播", "综合", "省市县",
                "http://stream9.jlntv.cn/aac_huadiangb/sd/live.m3u8"));
        list.add(new Station("通化县综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/thxgb/playlist.m3u8"));
        list.add(new Station("集安综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/jagb/playlist.m3u8"));
        list.add(new Station("辽源交通文艺广播", "交通", "省市县",
                "http://stream9.jlntv.cn/lyjtgb/playlist.m3u8"));
        list.add(new Station("辽源综合广播", "综合", "省市县",
                "http://stream9.jlntv.cn/lyrmgb/playlist.m3u8"));
        list.add(new Station("吉林音乐广播 东北亚音乐台", "文艺", "中央",
                "https://live-jlr.jlntv.cn/live/fm927.m3u8"));
        list.add(new Station("长春城市生活广播", "综合", "省市县",
                "http://ls.qingting.fm/live/4984.m3u8"));
        list.add(new Station("双辽综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/slgb/playlist.m3u8"));
        list.add(new Station("吉林健康娱乐广播 老歌听不够，经典永流传", "综合", "中央",
                "https://live-jlr.jlntv.cn/live/fm1019.m3u8"));
        list.add(new Station("乾安综合广播", "综合", "省市县",
                "http://stream7.jlntv.cn/qagb/playlist.m3u8"));
        list.add(new Station("前郭综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/qggb/playlist.m3u8"));
        list.add(new Station("九台区综合广播", "综合", "省市县",
                "http://stream9.jlntv.cn/aac_jiutaigb/sd/live.m3u8"));
        list.add(new Station("柳河综合广播", "综合", "省市县",
                "http://stream3.jlntv.cn/lhgb/playlist.m3u8"));
        list.add(new Station("吉林经济广播 老年频率", "财经", "中央",
                "https://live-jlr.jlntv.cn/live/fm953.m3u8"));
        list.add(new Station("吉林乡村广播", "乡村", "中央",
                "https://live-jlr.jlntv.cn/live/fm976.m3u8"));
        list.add(new Station("临江县综合广播", "综合", "省市县",
                "http://stream9.jlntv.cn/aac_linjianggb/sd/live.m3u8"));
        list.add(new Station("梨树北方交通之声(2)", "交通", "省市县",
                "http://stream3.jlntv.cn/lsgb/playlist.m3u8"));
        list.add(new Station("永吉县人民广播电台(2)", "综合", "省市县",
                "http://stream9.jlntv.cn/yongjigb/playlist.m3u8"));
        list.add(new Station("伊通综合广播（2）", "综合", "省市县",
                "http://stream3.jlntv.cn/ytgb/playlist.m3u8"));
        list.add(new Station("长春少儿与老年生活广播", "少儿", "省市县",
                "https://lhttp.qtfm.cn/live/4984/64k.mp3"));
        list.add(new Station("通榆综合广播", "综合", "省市县",
                "http://stream7.jlntv.cn/tygb/playlist.m3u8"));
        list.add(new Station("吉林新闻综合广播", "新闻", "中央",
                "https://live-jlr.jlntv.cn//live/am738.m3u8"));
        list.add(new Station("吉林交通广播", "交通", "中央",
                "https://live-jlr.jlntv.cn/live/fm1038.m3u8"));
        list.add(new Station("延边朝鲜语新闻综合广播 뉴스종합방송", "新闻", "省市县",
                "https://srs.iybtv.cn/audio/AM1206/index.m3u8"));
        list.add(new Station("吉林资讯广播", "新闻", "中央",
                "https://live-jlr.jlntv.cn/live/fm1001.m3u8"));
        list.add(new Station("吉林旅游广播", "综合", "中央",
                "https://live-jlr.jlntv.cn//live/fm1033.m3u8"));
        list.add(new Station("延边朝鲜语文艺生活广播 문예생활방송", "综合", "省市县",
                "https://srs.iybtv.cn/audio/FM1023/index.m3u8"));
        list.add(new Station("延边汉语新闻综合广播", "新闻", "省市县",
                "https://srs.iyb983.cn/audio/FM983/index.m3u8"));
        list.add(new Station("延边交通文艺广播", "交通", "省市县",
                "https://srs.iyb983.cn/audio/FM1059/index.m3u8"));
        list.add(new Station("德惠广播", "综合", "省市县",
                "http://stream11.jlntv.cn/aac_dehuigb/sd/live.m3u8"));
        list.add(new Station("抚松广播", "综合", "省市县",
                "http://stream7.jlntv.cn/aac_fsgb/sd/live.m3u8"));

        // ===== 辽宁（44 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("辽宁广播电视台音乐广播", "文艺", "中央",
                "http://lhttp.qingting.fm/live/1101/64k.mp3"));
        list.add(new Station("辽宁交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/20025/64k.mp3"));
        list.add(new Station("辽宁广播电视台都市广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/1099/64k.mp3"));
        list.add(new Station("辽宁都市广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/1099/64k.mp3?app"));
        list.add(new Station("沈阳新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/20024/64k.mp3"));
        list.add(new Station("辽宁生活广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/1102/64k.mp3?app"));
        list.add(new Station("大连金普新区FM104.3 综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318307/64k.mp3"));
        list.add(new Station("辽宁乡村广播", "乡村", "中央",
                "https://lhttp.qtfm.cn/live/20018/64k.mp3"));
        list.add(new Station("朝阳县人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20212211/64k.mp3"));
        list.add(new Station("海城新闻综合广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/15318107/64k.mp3"));
        list.add(new Station("辽宁广播电视台生活广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/1102/64k.mp3"));
        list.add(new Station("庄河人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022473/64k.mp3"));
        list.add(new Station("辽宁音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1101/64k.mp3?app"));
        list.add(new Station("辽宁资讯广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/5022018/64k.mp3"));
        list.add(new Station("辽阳交通文艺广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022030/64k.mp3"));
        list.add(new Station("辽阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022447/64k.mp3"));
        list.add(new Station("沈阳新民广播FM103.9", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022535/64k.mp3"));
        list.add(new Station("瓦房店人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20500094/64k.mp3"));
        list.add(new Station("DLR-3 大连体育广播", "体育", "省市县",
                "https://lhttp.qingting.fm/live/1085/64k.mp3"));
        list.add(new Station("辽宁之声", "综合", "中央",
                "https://lhttp.qingting.fm/live/1103/64k.mp3?app_id=web"));
        list.add(new Station("FM99.1大连都市之声广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1086/64k.mp3"));
        list.add(new Station("朝阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20715/64k.mp3"));
        list.add(new Station("黑山县广播电视台广播节目", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500119/64k.mp3"));
        list.add(new Station("大连体育广播", "体育", "省市县",
                "http://ls.qingting.fm/live/1085.m3u8"));
        list.add(new Station("朝阳经济广播 AM648", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/5021880/64k.mp3"));
        list.add(new Station("朝阳交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20719/64k.mp3"));
        list.add(new Station("幸福956 大连新城乡广播", "综合", "省市县",
                "http://ls.qingting.fm/live/1089.m3u8"));
        list.add(new Station("锦州经济广播", "财经", "省市县",
                "https://srs.beijihainews.com.cn/video/jjgb/index.m3u8"));
        list.add(new Station("锦州新闻广播", "新闻", "省市县",
                "https://srs.beijihainews.com.cn/video/FM927/index.m3u8"));
        list.add(new Station("锦州交通广播", "交通", "省市县",
                "https://srs.beijihainews.com.cn/video/jtgb/index.m3u8"));

        // ===== 湖北（35 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("武汉经典音乐广播·岁月悠长，听见好时光", "文艺", "省市县",
                "http://ls.qingting.fm/live/1297.m3u8"));
        list.add(new Station("武汉新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/20198/64k.mp3"));
        list.add(new Station("湖北楚天交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/1291/64k.mp3"));
        list.add(new Station("湖北之声（调频版）（2）", "综合", "中央",
                "https://lhttp.qingting.fm/live/1303/64k.mp3"));
        list.add(new Station("湖北经典音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1296/64k.mp3"));
        list.add(new Station("武汉交通广播·武汉市应急广播电台", "交通", "省市县",
                "http://ls.qingting.fm/live/4665.m3u8"));
        list.add(new Station("襄阳综合广播", "综合", "省市县",
                "http://ls.qingting.fm/live/1307.m3u8"));
        list.add(new Station("襄阳交通音乐广播", "交通", "省市县",
                "http://ls.qingting.fm/live/1308.m3u8"));
        list.add(new Station("宜昌都市生活广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20567/64k.mp3"));
        list.add(new Station("湖北经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/1295/64k.mp3"));
        list.add(new Station("襄阳文化教育广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5057/64k.mp3"));
        list.add(new Station("十堰交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20342/64k.mp3"));
        list.add(new Station("监利电台Jianli National Radio", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318507/64k.mp3"));
        list.add(new Station("黄冈交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20207776/64k.mp3"));
        list.add(new Station("十堰综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20338/64k.mp3"));
        list.add(new Station("黄石经济广播", "财经", "省市县",
                "https://lhttp.qingting.fm/live/3964/64k.mp3"));
        list.add(new Station("随州综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20853/64k.mp3"));
        list.add(new Station("湖北之声中波", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wx32hubzsgb/playlist.m3u8?wsSession=d8b4488aebb72eb396f72a83-174705023278003&wsIPSercert=cfb15583bad3e1e7c083a9a0d492bf48"));
        list.add(new Station("武穴人民广播电台", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022071/64k.mp3"));
        list.add(new Station("鄂州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/21025/64k.mp3"));
        list.add(new Station("随州交通经济广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/21027/64k.mp3"));
        list.add(new Station("红安县电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022646/64k.mp3"));
        list.add(new Station("荆州交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1312/64k.mp3"));
        list.add(new Station("团风县广播电视台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500189/64k.mp3"));
        list.add(new Station("蕲春综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022252/64k.mp3"));
        list.add(new Station("恩施交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022719/64k.mp3"));
        list.add(new Station("公安县综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5063/64k.mp3"));
        list.add(new Station("宜昌音乐生活广播", "文艺", "省市县",
                "https://liveplay.ycrmt.cn/fm/qct.m3u8"));
        list.add(new Station("宜昌交通广播", "交通", "省市县",
                "https://liveplay.ycrmt.cn/fm/jtt.m3u8"));
        list.add(new Station("宜昌新闻综合广播", "新闻", "省市县",
                "https://liveplay.ycrmt.cn/fm/xwt.m3u8"));
        list.add(new Station("恩施综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022718/64k.mp3"));
        list.add(new Station("汉川少儿广播", "少儿", "省市县",
                "https://stream.zeno.fm/6pa9cwlcb98vv"));
        list.add(new Station("麻城综合广播", "综合", "省市县",
                "https://video.yscxy.cn:8888/mctv3.m3u8?t=${Date.now()}"));
        list.add(new Station("黄冈新闻综合广播", "新闻", "省市县",
                "https://lhttp-hw.qtfm.cn/live/1301/64k.mp3"));

        // ===== 新疆（33 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("伊犁维语广播", "民族", "省市县",
                "http://lhttp.qingting.fm/live/5022688/64k.mp3"));
        list.add(new Station("乌鲁木齐维语广播", "民族", "省市县",
                "https://lhttp.qtfm.cn/live/1923/64k.mp3"));
        list.add(new Station("乌鲁木齐新闻广播", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/1918/64k.mp3"));
        list.add(new Station("察布查尔人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022610/64k.mp3"));
        list.add(new Station("伊犁文艺交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022689/64k.mp3"));
        list.add(new Station("乌鲁木齐旅游音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/1920/64k.mp3"));
        list.add(new Station("伊犁综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211711/64k.mp3"));
        list.add(new Station("乌鲁木齐交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1919/64k.mp3"));
        list.add(new Station("伊犁哈语广播", "民族", "省市县",
                "https://lhttp.qingting.fm/live/5022692/64k.mp3"));
        list.add(new Station("新疆维语文艺广播", "民族", "中央",
                "https://lhttp.qtfm.cn/live/20639/64k.mp3"));
        list.add(new Station("巴音郭楞交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5022104/64k.mp3"));
        list.add(new Station("新疆哈语广播", "民族", "中央",
                "https://lhttp.qtfm.cn/live/1908/64k.mp3"));
        list.add(new Station("塔城汉语综合广播", "民族", "省市县",
                "https://lhttp.qtfm.cn/live/20500031/64k.mp3"));
        list.add(new Station("托峰明珠交通音乐·温宿人民广播电台", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20207780/64k.mp3"));
        list.add(new Station("塔城市综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500145/64k.mp3"));
        list.add(new Station("昌吉交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20440/64k.mp3"));
        list.add(new Station("新疆维语综合广播", "民族", "中央",
                "http://ocj2.kksmg.com/ocj1/ocj1.m3u8"));
        list.add(new Station("阿克苏汉语综合广播城市之声 活力940", "民族", "省市县",
                "https://lhttp-hw.qtfm.cn/live/15318550/64k.mp3"));
        list.add(new Station("新疆汉语综合广播", "民族", "中央",
                "http://satellitepull.cnr.cn/live/wxxjlsgb/playlist.m3u8?wsSession=5047841c34cc76f1a161d1fe-174717812676938&wsIPSercert=29f90b67755e3a4646651f7aa8eb5fd6"));
        list.add(new Station("新疆柯尔克孜语广播", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wxxjkygb/playlist.m3u8?wsSession=e155c0d7812904556cc22af2-174709889276127&wsIPSercert=29f90b67755e3a4646651f7aa8eb5fd6"));
        list.add(new Station("克拉玛依汉语综合广播 FM92.6 AM1179", "民族", "省市县",
                "https://live-news.kelamayi.com.cn/hls/k926/index.m3u8"));
        list.add(new Station("克拉玛依维吾尔语综合广播 FM90.7 AM882", "综合", "省市县",
                "https://live-news.kelamayi.com.cn/hls/k907/index.m3u8"));
        list.add(new Station("克拉玛依音乐广播 FM97.1", "文艺", "省市县",
                "https://live-news.kelamayi.com.cn/hls/k971/index.m3u8"));

        // ===== 湖南（32 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("长沙交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/3967/64k.mp3"));
        list.add(new Station("长沙品味音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/4930/64k.mp3"));
        list.add(new Station("常德综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/15318208/64k.mp3"));
        list.add(new Station("益阳综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20314/64k.mp3"));
        list.add(new Station("长沙县综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/4930/64k.mp3"));
        list.add(new Station("邵阳综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20148/64k.mp3"));
        list.add(new Station("邵阳经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/20500058/64k.mp3"));
        list.add(new Station("郴州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20489/64k.mp3"));
        list.add(new Station("长沙新闻广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/4877/64k.mp3"));
        list.add(new Station("郴州交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20867/64k.mp3"));
        list.add(new Station("娄底交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20507/64k.mp3"));
        list.add(new Station("岳阳综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20989/64k.mp3"));
        list.add(new Station("常德交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/15318209/64k.mp3"));
        list.add(new Station("衡阳交通经济广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/15318385/64k.mp3"));
        list.add(new Station("岳阳交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20987/64k.mp3"));
        list.add(new Station("衡阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318386/64k.mp3"));
        list.add(new Station("靖州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500011/64k.mp3"));
        list.add(new Station("娄底综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/21213/64k.mp3"));
        list.add(new Station("宜章综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318691/64k.mp3"));
        list.add(new Station("湘潭交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/21269/64k.mp3"));
        list.add(new Station("湘潭新闻综合广播 私家车音乐广播FM88.2", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/15318549/64k.mp3"));
        list.add(new Station("桃江电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500086/64k.mp3"));
        list.add(new Station("龙山综合广播龙凤之声", "综合", "省市县",
                "https://liveplay-srs.voc.com.cn/hls/broadcast/146_80a15b.m3u8"));
        list.add(new Station("吉首综合广播飞扬101", "综合", "省市县",
                "https://liveplay-srs.voc.com.cn/hls/broadcast/143_a544b7.m3u8"));
        list.add(new Station("沅江综合广播", "综合", "省市县",
                "https://liveplay-srs.voc.com.cn/hls/broadcast/163_a8d90e.m3u8"));
        list.add(new Station("长沙城市之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/4237/64k.mp3"));

        // ===== 安徽（31 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("安徽交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/1949/64k.mp3"));
        list.add(new Station("安徽音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1947/64k.mp3"));
        list.add(new Station("安徽戏曲广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1952/64k.mp3"));
        list.add(new Station("安徽综合广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/4919/64k.mp3"));
        list.add(new Station("安徽生活广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/1948/64k.mp3"));
        list.add(new Station("界首之声", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20207785/64k.mp3"));
        list.add(new Station("芜湖音乐故事广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/5028/64k.mp3"));
        list.add(new Station("安庆综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1965/64k.mp3"));
        list.add(new Station("安庆农村广播", "乡村", "省市县",
                "https://lhttp.qingting.fm/live/1966/64k.mp3"));
        list.add(new Station("安徽经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/4916/64k.mp3"));
        list.add(new Station("亳州交通音乐广播", "交通", "省市县",
                "http://zbbf2.ahbztv.com/live/41c.m3u8"));
        list.add(new Station("安徽农村广播", "乡村", "中央",
                "https://lhttp.qtfm.cn/live/1950/64k.mp3"));
        list.add(new Station("铜陵综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/21303/64k.mp3"));
        list.add(new Station("池州综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022373/64k.mp3"));
        list.add(new Station("宣城综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022/64k.mp3"));
        list.add(new Station("安徽老年广播", "综合", "中央",
                "http://ls.qingting.fm/live/1951.m3u8"));
        list.add(new Station("安徽旅游广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/15318219/64k.mp3"));
        list.add(new Station("阜阳交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1971/64k.mp3"));
        list.add(new Station("阜阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1970/64k.mp3"));
        list.add(new Station("滁州南谯之声 经典983电台", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20211575/64k.mp3"));
        list.add(new Station("阜阳经济广播", "财经", "省市县",
                "https://lhttp.qtfm.cn/live/5022571/64k.mp3"));
        list.add(new Station("固镇人民广播电台", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20500125/64k.mp3"));
        list.add(new Station("繁昌新闻综合广播", "新闻", "省市县",
                "https://nfcplay.wuhunews.cn/fc/zhgb.m3u8"));
        list.add(new Station("广德综合广播 FM89.3", "综合", "省市县",
                "https://live.gdtv.ah.cn/live/FM893.m3u8"));
        list.add(new Station("泾县新闻综合广播", "新闻", "省市县",
                "https://apisave.jxnn.cn/hls/jxfm/playlist.m3u8"));

        // ===== 山西（30 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("山西故事广播", "文艺", "中央",
                "http://radiolive.sxrtv.com/live/gushi.m3u8"));
        list.add(new Station("山西音乐广播", "文艺", "中央",
                "http://radiolive.sxrtv.com/live/yinyue.m3u8"));
        list.add(new Station("山西交通广播", "交通", "中央",
                "http://radiolive.sxrtv.com/live/jiaotong.m3u8"));
        list.add(new Station("山西广播电视台故事广播", "文艺", "中央",
                "http://lhttp.qingting.fm/live/5022511/64k.mp3"));
        list.add(new Station("太原音乐广播", "文艺", "省市县",
                "https://lhttp.qingting.fm/live/1185/64k.mp3"));
        list.add(new Station("大同新闻综合广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/20211690/64k.mp3"));
        list.add(new Station("山西文艺广播", "综合", "中央",
                "http://radiolive.sxrtv.com/live/wenyi.m3u8"));
        list.add(new Station("晋城交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1189/64k.mp3"));
        list.add(new Station("太原老年之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20211701/64k.mp3"));
        list.add(new Station("山西综合广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/20491/64k.mp3?app"));
        list.add(new Station("大同交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/5022396/64k.mp3"));
        list.add(new Station("山西农村广播", "乡村", "中央",
                "http://radiolive.sxrtv.com/live/nongcun.m3u8"));
        list.add(new Station("长治交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5021851/64k.mp3"));
        list.add(new Station("山西经济广播", "财经", "中央",
                "http://radiolive.sxrtv.com/live/jingji.m3u8"));
        list.add(new Station("大同经济文艺广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/20211689/64k.mp3"));
        list.add(new Station("晋城综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1188/64k.mp3"));
        list.add(new Station("阳泉综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318568/64k.mp3"));
        list.add(new Station("长治综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021874/64k.mp3"));
        list.add(new Station("阳泉交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/15318165/64k.mp3"));
        list.add(new Station("山西健康之声广播", "综合", "中央",
                "http://radiolive.sxrtv.com/live/jiankang.m3u8"));
        list.add(new Station("阳泉经济广播", "财经", "省市县",
                "http://lhttp.qingting.fm/live/20211652/64k.mp3"));
        list.add(new Station("太原交通广播", "交通", "省市县",
                "https://lhttp-hw.qtfm.cn/live/4900/64k.mp3"));
        list.add(new Station("运城综合广播（官网直播）", "综合", "省市县",
                "https://live.0359tv.com/lsdream/4TTDb6q/live.m3u8"));
        list.add(new Station("运城文艺广播（官网直播）", "综合", "省市县",
                "https://live.0359tv.com/lsdream/p3e1Pzg/live.m3u8"));
        list.add(new Station("高平综合广播 FM88.5", "综合", "省市县",
                "https://live.gprmt.cn/aac_FM885/playlist.m3u8"));
        list.add(new Station("太谷综合广播", "综合", "省市县",
                "https://p2.vzan.com/slowlive/933376641866061786/live.m3u8"));

        // ===== 甘肃（28 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("甘肃交通广播", "交通", "中央",
                "https://lhttp.qingting.fm/live/3939/64k.mp3"));
        list.add(new Station("张掖新闻综合广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/5022096/64k.mp3"));
        list.add(new Station("天水交通广播频道", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20211613/64k.mp3"));
        list.add(new Station("天水交通广播", "交通", "省市县",
                "http://play.kankanlive.com/live/1735268253198106.m3u8"));
        list.add(new Station("庆阳新闻综合广播", "新闻", "省市县",
                "https://play.kankanlive.com/live/1712564707088949.m3u8"));
        list.add(new Station("会宁综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1721805639071291.m3u8"));
        list.add(new Station("陇西综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1733905755908367.m3u8"));
        list.add(new Station("定西综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1745232015441290.m3u8"));
        list.add(new Station("天水音乐文艺广播", "文艺", "省市县",
                "http://play.kankanlive.com/live/1735268150305110.m3u8"));
        list.add(new Station("天水综合广播", "综合", "省市县",
                "http://play.kankanlive.com/live/1735268289979102.m3u8"));
        list.add(new Station("酒泉综合广播 FM101.5", "综合", "省市县",
                "https://play.kankanlive.com/live/1743389402991299.m3u8"));
        list.add(new Station("平凉交通广播", "交通", "省市县",
                "https://play.kankanlive.com/live/1694762955080949.m3u8"));
        list.add(new Station("甘南综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1715588420818938.m3u8"));
        list.add(new Station("定西交通广播", "交通", "省市县",
                "https://play.kankanlive.com/live/1745231947900292.m3u8"));
        list.add(new Station("永昌综合广播FM106.2", "综合", "省市县",
                "https://play.kankanlive.com/live/1770863457995110.m3u8"));
        list.add(new Station("平凉综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1750649719216047.m3u8"));
        list.add(new Station("甘南藏语广播", "民族", "省市县",
                "https://play.kankanlive.com/live/1715588389099939.m3u8"));
        list.add(new Station("嘉峪关综合广播", "综合", "省市县",
                "https://play.kankanlive.com/live/1754366461934397.m3u8"));
        list.add(new Station("武威新闻综合广播 FM89.9", "新闻", "省市县",
                "https://play.kankanlive.com/live/1664439917433940.m3u8"));

        // ===== 江西（24 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("江西广播电视台新闻广播", "新闻", "中央",
                "http://lhttp.qingting.fm/live/1809/64k.mp3"));
        list.add(new Station("江西文艺音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/1802/64k.mp3"));
        list.add(new Station("江西综合新闻广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/1809/64k.mp3"));
        list.add(new Station("南昌交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1804/64k.mp3"));
        list.add(new Station("九江交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5021918/64k.mp3"));
        list.add(new Station("红调频·九江综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022729/64k.mp3"));
        list.add(new Station("赣州交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/4942/64k.mp3"));
        list.add(new Station("九江文化旅游广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20212210/64k.mp3"));
        list.add(new Station("赣州综合广播（2）", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20266/64k.mp3"));
        list.add(new Station("南康广播电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021869/64k.mp3"));
        list.add(new Station("江西广播电视台文艺音乐广播", "文艺", "中央",
                "http://lhttp.qingting.fm/live/1802/64k.mp3"));
        list.add(new Station("九江赣北之声广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022648/64k.mp3"));
        list.add(new Station("江西时尚广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/20500092/64k.mp3"));
        list.add(new Station("鹰潭交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5022036/64k.mp3"));
        list.add(new Station("鹰潭综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022035/64k.mp3"));
        list.add(new Station("景德镇综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022025/64k.mp3"));
        list.add(new Station("新余综合广播", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20178/64k.mp3"));
        list.add(new Station("德兴之声国际广播 DeXing International", "综合", "省市县",
                "http://player4.juyun.tv/camera/158562106.m3u8?"));
        list.add(new Station("玉山新闻综合广播", "新闻", "省市县",
                "https://play-a2.quklive.com/live/1744269984309138.m3u8"));

        // ===== 黑龙江（21 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("哈尔滨古典音乐广播", "文艺", "省市县",
                "http://lhttp.qingting.fm/live/5022338/64k.mp3"));
        list.add(new Station("Heart FM冰城102.6·哈尔滨古典音乐广播", "文艺", "省市县",
                "https://stream.hrbtv.net/gdpl/playlist.m3u8"));
        list.add(new Station("哈尔滨音乐广播", "文艺", "省市县",
                "https://stream.hrbtv.net/yypl/playlist.m3u8"));
        list.add(new Station("黑龙江音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/4969/64k.mp3"));
        list.add(new Station("哈尔滨文艺广播", "综合", "省市县",
                "https://stream.hrbtv.net/wypl/playlist.m3u8"));
        list.add(new Station("黑龙江新闻广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/4974/64k.mp3"));
        list.add(new Station("哈尔滨交通广播", "交通", "省市县",
                "https://stream.hrbtv.net/jtpl/playlist.m3u8"));
        list.add(new Station("哈尔滨综合广播", "综合", "省市县",
                "https://stream.hrbtv.net/xwpl/playlist.m3u8"));
        list.add(new Station("黑龙江生活广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/4970/64k.mp3"));
        list.add(new Station("哈尔滨经济广播", "财经", "省市县",
                "https://stream.hrbtv.net/876pl/playlist.m3u8"));
        list.add(new Station("巴彦淖尔综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1893/64k.mp3"));
        list.add(new Station("牡丹江综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022434/64k.mp3"));
        list.add(new Station("牡丹江交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/5022354/64k.mp3"));
        list.add(new Station("巴彦淖尔文艺生活广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1894/64k.mp3"));
        list.add(new Station("大庆交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20500061/64k.mp3"));
        list.add(new Station("大庆音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/20500212/64k.mp3"));
        list.add(new Station("巴彦淖尔交通广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/1895/64k.mp3"));
        list.add(new Station("大庆综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20500060/64k.mp3"));
        list.add(new Station("黑龙江朝语广播", "民族", "中央",
                "https://satellitepull.cnr.cn/live/wx32hljcygb/playlist.m3u8?wsSession=a919bd0baf31a1a6f3966f08-174778330216034&wsIPSercert=2683acdad9e689f89519c7ec3f7e218b"));
        list.add(new Station("伊春第一套广播", "综合", "省市县",
                "https://liveplaysr.dbw.cn/lsdream/lYKK5cb/2000/live.m3u8"));
        list.add(new Station("伊春第二套广播", "综合", "省市县",
                "https://liveplaysr.dbw.cn/lsdream/nktcdk0/2000/live.m3u8"));

        // ===== 云南（21 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("云南新闻广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/1926/64k.mp3"));
        list.add(new Station("昆明交通音乐广播", "交通", "省市县",
                "http://ls.qingting.fm/live/1936.m3u8"));
        list.add(new Station("昭通交通旅游广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/21247/64k.mp3"));
        list.add(new Station("云南民族广播", "民族", "中央",
                "https://lhttp.qtfm.cn/live/1933/64k.mp3"));
        list.add(new Station("德宏综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5021849/64k.mp3"));
        list.add(new Station("玉溪交通旅游广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20211563/64k.mp3"));
        list.add(new Station("云南经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/1927/64k.mp3"));
        list.add(new Station("普洱综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1938/64k.mp3"));
        list.add(new Station("开远人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022383/64k.mp3"));
        list.add(new Station("昆明城市管理广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1936/64k.mp3"));
        list.add(new Station("怒江综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/15318176/64k.mp3"));
        list.add(new Station("弥勒人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022531/64k.mp3"));
        list.add(new Station("云南国际广播 Yunnan International Radio", "综合", "中央",
                "https://gbw.ynradio.cn/radio/gjgb.stream/playlist.m3u8"));
        list.add(new Station("普洱交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20212429/64k.mp3"));
        list.add(new Station("昆明综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1934/64k.mp3"));
        list.add(new Station("蒙自市广播电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5021599/64k.mp3"));
        list.add(new Station("德宏交通旅游广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/5022548/64k.mp3"));
        list.add(new Station("昭通乌蒙之声新闻综合广播", "新闻", "省市县",
                "https://lhttp-hw.qtfm.cn/live/21249/64k.mp3"));
        list.add(new Station("德宏综合广播（民族语）", "民族", "省市县",
                "https://lhttp.qingting.fm/live/5021850/64k.mp3"));
        list.add(new Station("云南音乐广播", "文艺", "中央",
                "https://gbw.ynradio.cn/radio/yygb.stream/playlist.m3u8"));

        // ===== 上海（1 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("上海浦东FM100.1新娱乐广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/5022341/64k.mp3"));

        // ===== 中央（11 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("CRI中文环球广播·CRI chinese service", "综合", "中央",
                "http://sk.cri.cn/hyhq.m3u8"));
        list.add(new Station("CRI英语资讯广播 China Plus Radio", "新闻", "中央",
                "http://sk.cri.cn/am846.m3u8"));
        list.add(new Station("CRI环球资迅广播", "综合", "中央",
                "https://sk.cri.cn/905.m3u8"));
        list.add(new Station("CRI劲曲调频 HIT FM（成都）", "综合", "中央",
                "http://lhttp.qingting.fm/live/15318703/64k.mp3"));
        list.add(new Station("经济之声", "财经", "省市县",
                "https://ngcdn002.cnr.cn/live/jjzs/index.m3u8"));
        list.add(new Station("CRI南海之声", "综合", "中央",
                "http://sk.cri.cn/nhzs.m3u8"));
        list.add(new Station("CNR-2 经济之声伴音", "财经", "中央",
                "http://119.28.21.93/radio/cnr2.mp3"));
        list.add(new Station("CNR-6 中华之声伴音", "综合", "中央",
                "http://119.28.21.93/radio/cnr6.mp3"));
        list.add(new Station("CNR-7 神州之声伴音", "综合", "中央",
                "http://119.28.21.93/radio/cnr7.mp3"));

        // ===== 内蒙古（18 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("包头综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1889/64k.mp3"));
        list.add(new Station("包头交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/1890/64k.mp3"));
        list.add(new Station("鄂尔多斯交通文体广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/20352/64k.mp3"));
        list.add(new Station("乌海综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318706/64k.mp3"));
        list.add(new Station("乌海交通音乐广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/15318704/64k.mp3"));
        list.add(new Station("鄂尔多斯蒙语综合广播", "民族", "省市县",
                "http://lhttp.qingting.fm/live/20348/64k.mp3"));
        list.add(new Station("包头蒙语广播", "民族", "省市县",
                "https://lhttp.qingting.fm/live/1891/64k.mp3"));
        list.add(new Station("通辽汉语综合广播", "民族", "省市县",
                "https://video.tongliaowang.com:2443/live/cid2.m3u8"));
        list.add(new Station("乌海蒙语综合广播", "民族", "省市县",
                "http://live.wuhainews.org.cn/aac_myzhgb/sd/live.m3u8"));
        list.add(new Station("呼伦贝尔蒙语广播", "民族", "省市县",
                "https://satellitepull.cnr.cn/live/wx32nmghlbemygb/playlist.m3u8?wsSession=c650605f64c6488d38b66982-174814238782426&wsIPSercert=302e928c9e06924943d63c021170d763"));
        list.add(new Station("呼伦贝尔汉语广播", "民族", "省市县",
                "https://satellitepull.cnr.cn/live/wx32nmghlbehygb/playlist.m3u8?wsSession=d966c1f28016a758d88ba0c2-174814248595620&wsIPSercert=302e928c9e06924943d63c021170d763"));
        list.add(new Station("通辽蒙语综合广播", "民族", "省市县",
                "https://video.tongliaowang.com:2443/live/cid3.m3u8"));
        list.add(new Station("锡林郭勒汉语广播", "民族", "省市县",
                "https://satellitepull.cnr.cn/live/wx32nmgxlglhygb/playlist.m3u8?wsSession=d966c1f28016a758d88ba0c2-174779662031724&wsIPSercert=2683acdad9e689f89519c7ec3f7e218b"));
        list.add(new Station("通辽交通文艺广播", "交通", "省市县",
                "https://video.tongliaowang.com:2443/live/cid5.m3u8"));
        list.add(new Station("锡林郭勒蒙语广播", "民族", "省市县",
                "https://satellitepull.cnr.cn/live/wx32nmgxlglmygb/playlist.m3u8?wsSession=d966c1f28016a758d88ba0c2-174779643946624&wsIPSercert=2683acdad9e689f89519c7ec3f7e218b"));
        list.add(new Station("内蒙古城乡生活广播（视频直播线路）", "综合", "中央",
                "http://play1-qk.nmtv.cn/live/1902718general.m3u8"));

        // ===== 北京（8 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("北京古典音乐广播", "文艺", "中央",
                "https://stream.zeno.fm/qa9punk6ynhvv"));
        list.add(new Station("BRTV北京文艺广播", "综合", "中央",
                "https://brtv-radiolive.rbc.cn/alive/fm876.m3u8"));
        list.add(new Station("BRTV北京音乐广播", "文艺", "中央",
                "https://brtv-radiolive.rbc.cn/alive/fm974.m3u8"));
        list.add(new Station("BRTV北京新闻广播", "新闻", "中央",
                "https://brtv-radiolive.rbc.cn/alive/fm945.m3u8"));
        list.add(new Station("BRTV北京交通广播", "交通", "中央",
                "https://brtv-radiolive.rbc.cn/alive/fm1039.m3u8"));
        list.add(new Station("BRTV北京城市广播", "综合", "中央",
                "https://brtv-radiolive.rbc.cn/alive/fm1073.m3u8"));
        list.add(new Station("北京顺义区广播FM92.9", "综合", "中央",
                "https://livesy.chinamcache.com/live/gb01.m3u8"));
        list.add(new Station("北京延庆之声", "综合", "中央",
                "https://videoplaynew.yanqingrmzx.com/yq/gb.m3u8"));

        // ===== 天津（1 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("天津静海区广播电台", "综合", "中央",
                "http://lhttp.qingting.fm/live/20212227/64k.mp3"));

        // ===== 宁夏（3 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("宁夏音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/15318294/64k.mp3"));
        list.add(new Station("宁夏交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/1840/64k.mp3"));
        list.add(new Station("宁夏新闻广播", "新闻", "中央",
                "http://satellitepull.cnr.cn/live/wxnxxwgb/playlist.m3u8?wsSession=d8b4488aebb72eb396f72a83-174701144043039&wsIPSercert=5bb043949891952939522be8c9bd5410"));

        // ===== 广西（19 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("南宁综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/20358/64k.mp3"));
        list.add(new Station("广西文艺广播", "综合", "中央",
                "https://stream.bbrtv.com:10443/hls/rCjJ4Y1MR/rCjJ4Y1MR_live.m3u8"));
        list.add(new Station("广西经济广播", "财经", "中央",
                "https://stream.bbrtv.com:10443/hls/wrr24LJGR/wrr24LJGR_live.m3u8"));
        list.add(new Station("南宁交通音乐广播", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20767/64k.mp3"));
        list.add(new Station("广西教育广播", "综合", "中央",
                "https://stream.bbrtv.com:10443/hls/I7LJVL1Gg/I7LJVL1Gg_live.m3u8"));
        list.add(new Station("玉林综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/1762/64k.mp3"));
        list.add(new Station("贺州综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5043/64k.mp3"));
        list.add(new Station("北海广播电视台新闻综合广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/20861/64k.mp3"));
        list.add(new Station("北海交通音乐广播 BTR FM99.1", "交通", "省市县",
                "https://lhttp.qtfm.cn/live/20211621/64k.mp3"));
        list.add(new Station("广西旅游广播", "综合", "中央",
                "http://ls.qingting.fm/live/1753.m3u8"));
        list.add(new Station("百色综合广播 FM105.2", "综合", "省市县",
                "https://vod.bsyjrb.cn/live/9cb859019bb6.m3u8"));
        list.add(new Station("百色音乐广播 FM87.6", "文艺", "省市县",
                "https://vod.bsyjrb.cn/live/82c95bdd98f2.m3u8"));
        list.add(new Station("隆安综合广播（微赞视频慢直播线路）", "综合", "省市县",
                "https://p8.vzan.com/slowlive/665454783787149478/live.m3u8"));
        list.add(new Station("来宾综合广播", "综合", "省市县",
                "http://zb.gxlbamc.com:9300/hls/live/7712.m3u8"));
        list.add(new Station("贵港综合广播 金曲1019", "文艺", "省市县",
                "https://play-a2.quklive.com/live/1763082752100268.m3u8"));
        list.add(new Station("钦州综合广播", "综合", "省市县",
                "https://stream.gxqzxw.com/aac_zhgb986/fm/live.m3u8"));
        list.add(new Station("钦州音乐广播", "文艺", "省市县",
                "https://stream.gxqzxw.com/aac_audio889/playlist.m3u8"));

        // ===== 海南（15 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("三亚天涯之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/20450/64k.mp3"));
        list.add(new Station("海南新闻广播", "新闻", "中央",
                "http://lhttp.qingting.fm/live/1861/64k.mp3"));
        list.add(new Station("海南民生广播", "综合", "中央",
                "http://lhttp.qingting.fm/live/21243/64k.mp3"));
        list.add(new Station("海口交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/5022079/64k.mp3?"));
        list.add(new Station("三亚旅游之声103.8", "综合", "省市县",
                "http://lhttp.qingting.fm/live/15318203/64k.mp3"));
        list.add(new Station("海南音乐广播", "文艺", "中央",
                "http://lhttp.qingting.fm/live/4878/64k.mp3"));
        list.add(new Station("海南交通广播", "交通", "中央",
                "https://lhttp.qingting.fm/live/4911/64k.mp3"));
        list.add(new Station("海南国际旅游岛之声", "综合", "中央",
                "http://lhttp.qingting.fm/live/1862/64k.mp3"));
        list.add(new Station("海南旅游广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/1862/64k.mp3"));
        list.add(new Station("海口音乐广播 WHIZRADIO", "文艺", "省市县",
                "https://live2.hkbtv.cn/live/fm916.m3u8"));
        list.add(new Station("东方音乐电台 THMR", "文艺", "省市县",
                "https://radio.yunmoan.cn/radio/touhou/320k"));
        list.add(new Station("海口旅游交通广播", "交通", "省市县",
                "https://live2.hkbtv.cn/live/fm954.m3u8"));
        list.add(new Station("海口综合广播 FM101.8", "综合", "省市县",
                "https://live2.hkbtv.cn/live/fm1018.m3u8"));
        list.add(new Station("海口生活广播 FM104.4", "综合", "省市县",
                "https://live2.hkbtv.cn/live/fm1044.m3u8"));

        // ===== 福建（20 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("泉州刺桐之声", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022360/64k.mp3"));
        list.add(new Station("厦门综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1737/64k.mp3"));
        list.add(new Station("福州新闻广播", "新闻", "省市县",
                "http://lhttp.qingting.fm/live/5025/64k.mp3"));
        list.add(new Station("厦门音乐广播", "文艺", "省市县",
                "https://lhttp.qtfm.cn/live/1739/64k.mp3"));
        list.add(new Station("厦门经济交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1738/64k.mp3"));
        list.add(new Station("福建新闻综合广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/1731/64k.mp3"));
        list.add(new Station("泉州新闻综合广播", "新闻", "省市县",
                "https://lhttp.qingting.fm/live/15318346/64k.mp3"));
        list.add(new Station("泉州交通广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/15318189/64k.mp3"));
        list.add(new Station("福建交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/1733/64k.mp3"));
        list.add(new Station("福建经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/1732/64k.mp3"));
        list.add(new Station("福州左海之声", "综合", "省市县",
                "http://lhttp.qingting.fm/live/3937/64k.mp3"));
        list.add(new Station("福建都市生活广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/1736/64k.mp3"));
        list.add(new Station("福州交通之声", "交通", "省市县",
                "http://lhttp.qingting.fm/live/5026/64k.mp3"));
        list.add(new Station("漳州综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/1742/64k.mp3"));
        list.add(new Station("漳州交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/1743/64k.mp3"));
        list.add(new Station("三明综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022100/64k.mp3"));
        list.add(new Station("宁德交通旅游广播", "交通", "省市县",
                "https://live.0593tv.cn/live/1x71vlpybkk5.m3u8"));
        list.add(new Station("宁德综合广播", "综合", "省市县",
                "https://live.0593tv.cn/live/grhg5jjxnev6.m3u8"));

        // ===== 网络（109 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("两广之声音乐台", "文艺", "网络",
                "https://lhttp.qtfm.cn/live/20500149/64k.mp3"));
        list.add(new Station("故事广播-阿基米德故事会", "文艺", "网络",
                "https://lhttp.qtfm.cn/live/20500182/64k.mp3"));
        list.add(new Station("南方生活广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/468/64k.mp3"));
        list.add(new Station("夜愿电台Nightwish Radio", "综合", "网络",
                "https://stream.zeno.fm/98wt67yfky8uv"));
        list.add(new Station("和谐铁路之声", "综合", "网络",
                "https://stream.zeno.fm/gzsk9r47sc9uv"));
        list.add(new Station("复兴电台（测试）", "综合", "网络",
                "http://202.39.43.67:1935/live/RA000024/chunklist.m3u8"));
        list.add(new Station("_Wawa Radio 101.2 FM", "综合", "网络",
                "https://xn--tmz.xn--6frz82g/streams"));
        list.add(new Station("京津冀之声", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5022463/64k.mp3"));
        list.add(new Station("Tiktok网络电台-英语频道", "综合", "网络",
                "https://stream.zeno.fm/advmk81bu68uv"));
        list.add(new Station("中国之声", "综合", "网络",
                "https://ngcdn001.cnr.cn/live/zgzs/index.m3u8"));
        list.add(new Station("黄岛综合广播", "综合", "网络",
                "https://lhttp.qingting.fm/live/20176/64k.mp3"));
        list.add(new Station("大兴区人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/5021739/64k.mp3"));
        list.add(new Station("BRTV京津冀之声", "综合", "网络",
                "https://brtv-radiolive.rbc.cn/alive/fm1006.m3u8"));
        list.add(new Station("故城县经典音乐FM90.5", "文艺", "网络",
                "http://lhttp.qingting.fm/live/20212269/64k.mp3?"));
        list.add(new Station("圆环之理广播电台", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5022308/64k.mp3"));
        list.add(new Station("武安人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/5022474/64k.mp3"));
        list.add(new Station("十三师广播电视台FM106.5", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5022506/64k.mp3"));
        list.add(new Station("献县人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/5022603/64k.mp3"));
        list.add(new Station("郾城人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/15318300/64k.mp3"));
        list.add(new Station("永川之声100.7", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20210236/64k.mp3"));
        list.add(new Station("永年人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/20212203/64k.mp3"));
        list.add(new Station("FM98.6城市音乐广播·信都区融媒体中心", "文艺", "网络",
                "https://lhttp.qingting.fm/live/20500196/64k.mp3"));
        list.add(new Station("金山人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/4022/64k.mp3"));
        list.add(new Station("T-Radio旅游广播104", "综合", "网络",
                "https://stream.zeno.fm/4x3gkv9zsy8uv"));
        list.add(new Station("清苑区105.8飞扬调频", "综合", "网络",
                "http://lhttp.qingting.fm/live/5021803/64k.mp3?"));
        list.add(new Station("宿豫人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/5005/64k.mp3"));
        list.add(new Station("巴南综合广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5022385/64k.mp3"));
        list.add(new Station("任丘人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/5022470/64k.mp3"));
        list.add(new Station("信仰广播一套", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5021977/64k.mp3"));
        list.add(new Station("河间综合广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/15318503/64k.mp3"));
        list.add(new Station("魏县人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/20212412/64k.mp3"));
        list.add(new Station("栾城人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/5022038/64k.mp3"));
        list.add(new Station("鼎城人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/5021860/64k.mp3"));
        list.add(new Station("祥符广播919", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500156/64k.mp3"));
        list.add(new Station("泊头市广播电视台广播节目", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500164/64k.mp3"));
        list.add(new Station("五师人民广播电台双河之声", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20207772/64k.mp3"));
        list.add(new Station("大丰人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/20211708/64k.mp3"));
        list.add(new Station("绥中综合广播", "综合", "网络",
                "http://lhttp.qingting.fm/live/20211705/64k.mp3"));
        list.add(new Station("国际旅游岛之声", "综合", "网络",
                "http://ls.qingting.fm/live/1862.m3u8"));
        list.add(new Station("宛城都市音乐广播", "文艺", "网络",
                "https://lhttp.qingting.fm/live/5022725/64k.mp3"));
        list.add(new Station("义安电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/5021979/64k.mp3"));
        list.add(new Station("凉山州综合广播", "综合", "网络",
                "https://lhttp.qingting.fm/live/5022143/64k.mp3"));
        list.add(new Station("寒亭人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/4865/64k.mp3"));
        list.add(new Station("安溪FM946茶频率", "综合", "网络",
                "https://lhttp.qtfm.cn/live/5022135/64k.mp3"));
        list.add(new Station("缙云广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXjinyunaud/128k.m3u8"));
        list.add(new Station("旌阳区综合广播", "综合", "网络",
                "https://lhttp.qingting.fm/live/5021933/64k.mp3"));
        list.add(new Station("敦化综合广播", "综合", "网络",
                "http://stream9.jlntv.cn/dhgb/playlist.m3u8"));
        list.add(new Station("武进人民广播电台(2)", "综合", "网络",
                "http://live.wjyanghu.com/live/CH6.m3u8"));
        list.add(new Station("集美广播", "综合", "网络",
                "http://lhttp.qingting.fm/live/5022479/64k.mp3"));
        list.add(new Station("龙井综合广播", "综合", "网络",
                "http://stream9.jlntv.cn/longjinggb/playlist.m3u8"));
        list.add(new Station("浦东文艺生活广播 城市沸点FM100.1", "综合", "网络",
                "https://lhttp-hw.qtfm.cn/live/5022341/64k.mp3"));
        list.add(new Station("济源广播电台FM102.0", "综合", "网络",
                "https://lhttp.qingting.fm/live/5022142/64k.mp3"));
        list.add(new Station("綦江综合广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500201/64k.mp3"));
        list.add(new Station("垣曲人民广播电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/20500090/64k.mp3"));
        list.add(new Station("滨城广播电视台", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500168/64k.mp3"));
        list.add(new Station("天门综合广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500199/64k.mp3"));
        list.add(new Station("呼图壁县5G智慧电台", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500188/64k.mp3"));
        list.add(new Station("珲春综合广播", "综合", "网络",
                "http://stream9.jlntv.cn/hcgb/playlist.m3u8"));
        list.add(new Station("CNR-8 民族之声 조선어방송  ", "民族", "网络",
                "https://satellitepull.cnr.cn/live/wxmzzs/playlist.m3u8?wsSession=a919bd0baf31a1a6f3966f08-174761703245334&wsIPSercert=270c8446fe14303f2433a2f164ccd9e5"));
        list.add(new Station("景县综合广播", "综合", "网络",
                "https://lhttp.qtfm.cn/live/20500192/64k.mp3"));
        list.add(new Station("江源综合广播", "综合", "网络",
                "http://stream9.jlntv.cn/jiangyuangb/playlist.m3u8"));
        list.add(new Station("肃州广播电视台FM106.6", "综合", "网络",
                "https://lhttp-hw.qtfm.cn/live/15318602/64k.mp3"));
        list.add(new Station("淄川广播电视台", "综合", "网络",
                "https://lhttp-hw.qtfm.cn/live/20211598/64k.mp3"));
        list.add(new Station("晋江综合广播", "综合", "网络",
                "https://live.ijjnews.com/jjrd/sd/live.m3u8"));
        list.add(new Station("优优宝贝广播", "综合", "网络",
                "https://stream.zeno.fm/de26yhn9yy8uv"));
        list.add(new Station("⁡⁡⁡建平综合广播", "综合", "网络",
                "https://lhttp-hw.qtfm.cn/live/15318332/64k.mp3"));
        list.add(new Station("开州综合广播", "综合", "网络",
                "http://183.64.174.171:10124/tlgb.m3u8"));
        list.add(new Station("津南之声", "综合", "网络",
                "http://play.jinnantv.top/live/JNTV2.m3u8"));
        list.add(new Station("北仑电台", "综合", "网络",
                "https://lhttp.qingting.fm/live/1153/64k.mp3"));
        list.add(new Station("普陀广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXputuoaud/128k.m3u8"));
        list.add(new Station("松阳广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXsongyangaud/128k.m3u8"));
        list.add(new Station("CNR-1 中国之声伴音", "综合", "网络",
                "http://119.28.21.93/radio/cnr1.mp3"));
        list.add(new Station("柯桥人民广播电台", "综合", "网络",
                "http://lhttp.qingting.fm/live/2422/64k.mp3"));
        list.add(new Station("云和广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXyunheaud/128k.m3u8"));
        list.add(new Station("庆元广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXqingyuanaud/128k.m3u8"));
        list.add(new Station("玛纳斯综合广播", "综合", "网络",
                "http://218.84.127.245:1027/hls/main0/playlist.m3u8"));
        list.add(new Station("遂昌综合广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXsuichangaud/128k.m3u8"));
        list.add(new Station("石狮综合广播", "综合", "网络",
                "https://live-new.chinashishi.net/SSradio/sd/live.m3u8"));
        list.add(new Station("CNR-4 文艺之声伴音", "综合", "网络",
                "http://119.28.21.93/radio/cnr4.mp3"));
        list.add(new Station("CNR-5 老年之声伴音", "综合", "网络",
                "http://119.28.21.93/radio/cnr5.mp3"));
        list.add(new Station("延吉汉语·朝鲜语综合广播", "民族", "网络",
                "http://stream7.jlntv.cn/yjgb/playlist.m3u8"));
        list.add(new Station("双阳综合广播", "综合", "网络",
                "http://stream11.jlntv.cn/aac_shuangyanggb/sd/live.m3u8"));
        list.add(new Station("青田广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXqingtianaud/128k.m3u8"));
        list.add(new Station("衢江广播", "综合", "网络",
                "http://l.cztvcloud.com/channels/lantian/SXqujiangaud/128k.m3u8"));
        list.add(new Station("CNR-3 音乐之声伴音", "文艺", "网络",
                "http://119.28.21.93/radio/cnr3.mp3"));

        // ===== 西藏（5 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("西藏汉语广播", "民族", "中央",
                "http://satellitepull.cnr.cn/live/wxxzhygb/playlist.m3u8?wsSession=d8b4488aebb72eb396f72a83-174700937926481&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("日喀则综合广播", "综合", "省市县",
                "https://consolestream.rkzrm.cn:20323/0.m3u8"));
        list.add(new Station("西藏藏语广播", "民族", "中央",
                "http://satellitepull.cnr.cn/live/wxxzzygb/playlist.m3u8?wsSession=51c2a224185caba966715acd-174700996678387&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("西藏都市生活广播", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wxxzdsshgb/playlist.m3u8?wsSession=51c2a224185caba966715acd-174700953160318&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("西藏康巴话广播", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wxxzzykbfy/playlist.m3u8?wsSession=6136affdc49091afe49e248f-174701006672980&wsIPSercert=5bb043949891952939522be8c9bd5410"));

        // ===== 贵州（15 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("黔东南交通广播", "交通", "省市县",
                "http://lhttp.qingting.fm/live/5022285/64k.mp3"));
        list.add(new Station("贵州交通广播", "交通", "中央",
                "http://lhttp.qingting.fm/live/20057/64k.mp3"));
        list.add(new Station("贵州音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/20067/64k.mp3"));
        list.add(new Station("贵阳综合广播", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/1773/64k.mp3"));
        list.add(new Station("黔东南州凯里广播电台", "综合", "省市县",
                "https://lhttp.qtfm.cn/live/5022045/64k.mp3"));
        list.add(new Station("六盘水综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20211616/64k.mp3"));
        list.add(new Station("七星关人民广播电台", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5021866/64k.mp3"));
        list.add(new Station("安顺综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5022203/64k.mp3"));
        list.add(new Station("遵义交通文艺广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20741/64k.mp3"));
        list.add(new Station("贵州故事广播", "文艺", "中央",
                "http://satellitepull.cnr.cn/live/wx32gzgsgb/playlist.m3u8?wsSession=d8b4488aebb72eb396f72a83-174701428079898&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("贵州经济广播", "财经", "中央",
                "https://lhttp.qtfm.cn/live/20065/64k.mp3"));
        list.add(new Station("铜仁交通旅游广播", "交通", "省市县",
                "https://lhttp.qingting.fm/live/20211615/64k.mp3"));
        list.add(new Station("贵州综合广播", "综合", "中央",
                "http://satellitepull.cnr.cn/live/wx32gzwxwzhgb/playlist.m3u8?wsSession=d8b4488aebb72eb396f72a83-174701403997765&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("桐梓电台 综合广播", "综合", "省市县",
                "https://lhttp-hw.qtfm.cn/live/20212410/64k.mp3"));
        list.add(new Station("织金人民广播电台综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/20500037/64k.mp3"));

        // ===== 重庆（5 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("重庆交通广播", "交通", "中央",
                "http://ls.qingting.fm/live/1500.m3u8"));
        list.add(new Station("重庆音乐广播", "文艺", "中央",
                "http://ls.qingting.fm/live/647.m3u8"));
        list.add(new Station("重庆之声", "综合", "中央",
                "http://ls.qingting.fm/live/1498.m3u8"));
        list.add(new Station("重庆私家车广播", "综合", "中央",
                "http://ls.qingting.fm/live/1502.m3u8"));
        list.add(new Station("重庆都市广播", "综合", "中央",
                "https://lhttp.qtfm.cn/live/1502/64k.mp3"));

        // ===== 陕西（17 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("陕西交通广播", "交通", "中央",
                "https://lhttp.qtfm.cn/live/1601/64k.mp3"));
        list.add(new Station("陕西新闻广播", "新闻", "中央",
                "https://lhttp.qtfm.cn/live/1600/64k.mp3"));
        list.add(new Station("陕西音乐广播", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/4873/64k.mp3"));
        list.add(new Station("咸阳综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022397/64k.mp3"));
        list.add(new Station("陕西农村广播", "乡村", "中央",
                "https://lhttp.qtfm.cn/live/1602/64k.mp3"));
        list.add(new Station("陕西青少广播好听1055（经典流行金曲）", "文艺", "中央",
                "https://lhttp.qtfm.cn/live/4885/64k.mp3"));
        list.add(new Station("安康综合广播", "综合", "省市县",
                "http://lhttp.qingting.fm/live/5021861/64k.mp3"));
        list.add(new Station("渭南综合广播", "综合", "省市县",
                "https://lhttp.qingting.fm/live/5022388/64k.mp3"));
        list.add(new Station("榆林生活资讯广播", "新闻", "省市县",
                "https://hplayer1.juyun.tv/camera/115986128.m3u8"));
        list.add(new Station("榆林综合广播", "综合", "省市县",
                "https://hplayer1.juyun.tv/camera/115987102.m3u8"));
        list.add(new Station("榆林交通文艺广播", "交通", "省市县",
                "https://hplayer1.juyun.tv/camera/115978148.m3u8"));
        list.add(new Station("延安综合广播", "综合", "省市县",
                "https://srs.yanews.cn/audio/FM107-7/index.m3u8"));
        list.add(new Station("延安交通音乐广播", "交通", "省市县",
                "https://srs.yanews.cn/audio/FM98-7/index.m3u8"));
        list.add(new Station("西安新闻广播", "新闻", "省市县",
                "https://lhttp.qtfm.cn/live/1610/64k.mp3"));

        // ===== 青海（6 个 · 2026-09-23 补 · 按票数排序）=====
        list.add(new Station("青海安多藏语广播", "民族", "中央",
                "http://satellitepull.cnr.cn/live/wx32qhzygb/playlist.m3u8?wsSession=1c983a4091d3a5f8a963629b-174701116820119&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("青海新闻综合广播 青海之声", "新闻", "中央",
                "http://satellitepull.cnr.cn/live/wx32qhwxzhgb/playlist.m3u8?wsSession=1c983a4091d3a5f8a963629b-174700972161410&wsIPSercert=5bb043949891952939522be8c9bd5410"));
        list.add(new Station("西宁新闻综合广播 FM95.6", "新闻", "省市县",
                "http://lhttp.qtfm.cn/live/5022282/64k.mp3"));
        list.add(new Station("西宁交通文艺广播 FM104.3", "交通", "省市县",
                "http://lhttp.qtfm.cn/live/5022283/64k.mp3"));

        return list;
    }

    /**
     * 已确认不可用的源 —— 保留下来避免以后有人再试一遍。UI 不展示。
     *
     * 注意：这里**不含蜻蜓系**。蜻蜓是好的，详见类注释里的 UA 黑名单说明。
     */
    public static String[] knownDeadNotes() {
        return new String[]{
            "CNR 央广 ngcdn00X.cnr.cn: 仅 001/002 存活且为 HLS —— 官方源改用 satellitepull.cnr.cn（见「央广省级」分类）",
            "中国国际广播电台 / 各地方台官网流: 多为 HLS 或需要 Referer",
            "江苏新闻广播 (lzlive.vojs.cn): 返回 206 但只有 342 字节，实际无音频",
            "提醒：判断源是否存活时不要带 WinampMPEG 的 User-Agent —— 蜻蜓会 403，会误判",
            "提醒：不要带 curl 默认 UA 探测 —— zeno.fm 对默认 UA 返 401，用 Stagefright 才 200",
            "提醒：不要用 16 路并发测速 —— 会把本机出口打爆，得到「全部失败」的假象（实测踩过两次）",
        };
    }
}
