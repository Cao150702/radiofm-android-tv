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
 * HLS 台的内容分布（2026-09-21 实测，归在「央广·卫视」分类里，region 一律「中央」）：
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
        list.add(new Station("北京新闻广播", "新闻", "北京",
                "http://lhttp.qtfm.cn/live/339/64k.mp3",
                "https://lhttp.qtfm.cn/live/339/64k.mp3"));           // v=1198
        list.add(new Station("北京交通广播", "交通", "北京",
                "http://lhttp.qingting.fm/live/336/64k.mp3",
                "https://lhttp.qingting.fm/live/336/64k.mp3"));       // v=637
        list.add(new Station("北京文艺广播", "文艺", "北京",
                "http://lhttp.qtfm.cn/live/333/64k.mp3",
                "https://lhttp.qtfm.cn/live/333/64k.mp3"));           // v=539
        list.add(new Station("北京音乐广播", "音乐", "北京",
                "http://lhttp.qtfm.cn/live/332/64k.mp3",
                "https://lhttp.qtfm.cn/live/332/64k.mp3"));           // v=355

        // ================= 大陆 · 上海 =================
        list.add(new Station("上海新闻广播", "新闻", "上海",
                "http://lhttp.qingting.fm/live/270/64k.mp3"));
        list.add(new Station("上海东广新闻台", "新闻", "上海",
                "http://lhttp.qingting.fm/live/275/64k.mp3"));
        list.add(new Station("上海动感101", "流行", "上海",
                "http://lhttp.qingting.fm/live/274/64k.mp3",
                "https://lhttp.qingting.fm/live/274/64k.mp3"));       // v=774
        list.add(new Station("上海经典音乐广播", "经典", "上海",
                "http://lhttp.qingting.fm/live/267/64k.mp3"));
        list.add(new Station("上海音乐广播", "音乐", "上海",
                "http://lhttp.qingting.fm/live/273/64k.mp3"));

        // ================= 大陆 · 广东 =================
        list.add(new Station("广东新闻广播", "新闻", "广东",
                "http://lhttp.qtfm.cn/live/1254/64k.mp3",
                "https://lhttp.qtfm.cn/live/1254/64k.mp3"));          // v=1225
        list.add(new Station("广东珠江经济台", "经济", "广东",
                "http://lhttp.qtfm.cn/live/1259/64k.mp3",
                "https://lhttp.qtfm.cn/live/1259/64k.mp3"));          // v=1871
        list.add(new Station("广东音乐之声", "音乐", "广东",
                "http://lhttp.qtfm.cn/live/1260/64k.mp3",
                "https://lhttp.qtfm.cn/live/1260/64k.mp3"));          // v=1261
        list.add(new Station("广东交通之声", "交通", "广东",
                "http://lhttp.qtfm.cn/live/1262/64k.mp3",
                "https://lhttp.qtfm.cn/live/1262/64k.mp3"));          // v=992
        list.add(new Station("广东股市广播", "财经", "广东",
                "http://lhttp.qtfm.cn/live/4847/64k.mp3",
                "https://lhttp.qtfm.cn/live/4847/64k.mp3"));          // v=902
        list.add(new Station("广东城市之声", "综合", "广东",
                "http://lhttp.qtfm.cn/live/469/64k.mp3",
                "https://lhttp.qtfm.cn/live/469/64k.mp3"));           // v=515
        list.add(new Station("深圳新闻广播", "新闻", "广东",
                "http://lhttp.qingting.fm/live/1270/64k.mp3"));
        list.add(new Station("广州金曲音乐广播", "音乐", "广东",
                "http://lhttp.qingting.fm/live/20192/64k.mp3"));
        list.add(new Station("广州新闻资讯广播", "新闻", "广东",
                "http://lhttp.qingting.fm/live/4848/64k.mp3"));
        list.add(new Station("顺德音乐之声", "音乐", "广东",
                "http://lhttp.qtfm.cn/live/20500150/64k.mp3",
                "https://lhttp.qtfm.cn/live/20500150/64k.mp3"));      // v=602

        // ================= 大陆 · 其他省市 =================
        list.add(new Station("四川新闻广播", "新闻", "四川",
                "http://lhttp.qtfm.cn/live/4906/64k.mp3",
                "https://lhttp.qtfm.cn/live/4906/64k.mp3"));          // v=421
        list.add(new Station("江苏经典流行音乐广播", "经典", "江苏",
                "http://lhttp.qtfm.cn/live/4938/64k.mp3",
                "https://lhttp.qtfm.cn/live/4938/64k.mp3"));          // v=366
        list.add(new Station("河南星河音乐广播", "音乐", "河南",
                "http://lhttp.qingting.fm/live/20210755/64k.mp3"));
        list.add(new Station("郑州新闻广播", "新闻", "河南",
                "http://lhttp.qingting.fm/live/1220/64k.mp3"));
        list.add(new Station("济南故事广播", "故事", "山东",
                "http://lhttp.qtfm.cn/live/1672/64k.mp3",
                "https://lhttp.qtfm.cn/live/1672/64k.mp3"));          // v=687
        list.add(new Station("安徽小说评书广播", "评书", "安徽",
                "http://lhttp.qtfm.cn/live/1951/64k.mp3",
                "https://lhttp.qtfm.cn/live/1951/64k.mp3"));          // v=1675
        list.add(new Station("长沙 BIG RADIO 流行音乐", "流行", "湖南",
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
        list.add(new Station("Anison 动漫音乐台", "动漫", "日本",
                "http://pool.anison.fm:9000/AniSonFM(320)"));
        list.add(new Station("Big B Radio 亚洲音乐台", "亚洲流行", "网络",
                "https://antares.dribbcast.com/proxy/apop?mp=/s",
                "https://antares.dribbcast.com/proxy/cpop?mp=/s",
                "http://antares.dribbcast.com/proxy/apop?mp=/s"));
        list.add(new Station("Chinese Music World 华语音乐", "华语", "网络",
                "https://radio.chinesemusicworld.com/chinesemusic.mp3",
                "http://radio.chinesemusicworld.com/chinesemusic.mp3"));
        list.add(new Station("Acast 华语电台", "综合", "网络",
                "https://acast01.kolorboxlab.com/radio/8010/radio.mp3",
                "http://acast01.kolorboxlab.com/radio/8010/radio.mp3"));
        list.add(new Station("法国国际广播 RFI 中文", "新闻", "法国",
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
        list.add(new Station("Swiss News 瑞士新闻", "新闻", "瑞士",
                "https://replaynewszh.ice.infomaniak.ch/replaynewszh-128.mp3",
                "http://replaynewszh.ice.infomaniak.ch/replaynewszh-128.mp3"));
        list.add(new Station("Curiosity 电台", "综合", "国际",
                "http://curiosity.shoutca.st:8019/stream"));


        // ================= 蜻蜓地方台（341 个，全部实测 200 且为 MP3 —— 4.4 直接可播） =================
        list.add(new Station(" FM100.8 包河之声", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/5022668/64k.mp3"));
        list.add(new Station("AsiaFM安岳综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5022417/64k.mp3"));
        list.add(new Station("FM101仙居融媒体广播", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/5021908/64k.mp3"));
        list.add(new Station("FM102.4 靖江广播电台（蜻蜓mp3线路）", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/15318120/64k.mp3"));
        list.add(new Station("FM104.1北岳之声 浑源人民广播电台", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/20212209/64k.mp3"));
        list.add(new Station("FM104.7 澧县人民广播电台", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/15318178/64k.mp3"));
        list.add(new Station("FM105平阳电台", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/5022624/64k.mp3"));
        list.add(new Station("FM98.3如皋人民广播电台", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20207734/64k.mp3"));
        list.add(new Station("Nice Radio 永安广播电视台综合广播", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/15318388/64k.mp3"));
        list.add(new Station("Radio Impetus 心动电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500161/64k.mp3"));
        list.add(new Station("VOK 库尔勒梨城之声 FM105.3", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20212425/64k.mp3"));
        list.add(new Station("万宁综合广播", "HLS", "海南",
                "https://lhttp-hw.qtfm.cn/live/20500237/64k.mp3"));
        list.add(new Station("万盛融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318480/64k.mp3"));
        list.add(new Station("三台人民广播电台", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/15318544/64k.mp3"));
        list.add(new Station("上海交通广播 FM105.7", "HLS", "上海",
                "https://lhttp-hw.qtfm.cn/live/266/64k.mp3"));
        list.add(new Station("上海戏剧曲艺广播 AM1197 FM97.2", "HLS", "上海",
                "https://lhttp-hw.qtfm.cn/live/269/64k.mp3"));
        list.add(new Station("上海第一财经广播 FM90.9", "HLS", "上海",
                "https://lhttp-hw.qtfm.cn/live/276/64k.mp3"));
        list.add(new Station("东海综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500220/64k.mp3"));
        list.add(new Station("东港新东港广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/5022186/64k.mp3"));
        list.add(new Station("东源电台 FM101.5", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/20500124/64k.mp3"));
        list.add(new Station("中国·汤阴 FM90.5", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20500205/64k.mp3"));
        list.add(new Station("临沂音乐科教广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/4017/64k.mp3"));
        list.add(new Station("丹阳市融媒体中心综合广播 FM97.9 丹阳之声", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20207749/64k.mp3"));
        list.add(new Station("习水综合广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/15318201/64k.mp3"));
        list.add(new Station("云南交通之声", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/1928/64k.mp3"));
        list.add(new Station("云霄综合广播", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/20500106/64k.mp3"));
        list.add(new Station("京哈高速沿线广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022520/64k.mp3"));
        list.add(new Station("仁寿人民广播电台", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5021453/64k.mp3"));
        list.add(new Station("仙桃综合广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/20211562/64k.mp3"));
        list.add(new Station("佛冈电台", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/15318379/64k.mp3"));
        list.add(new Station("保山综合广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/5022446/64k.mp3"));
        list.add(new Station("健康广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20470/64k.mp3"));
        list.add(new Station("公主岭综合广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/20212386/64k.mp3"));
        list.add(new Station("兰考综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20500167/64k.mp3"));
        list.add(new Station("兴义市广播电视台", "HLS", "贵州",
                "https://lhttp-hw.qtfm.cn/live/20500110/64k.mp3"));
        list.add(new Station("兴仁广播", "HLS", "贵州",
                "https://lhttp-hw.qtfm.cn/live/20500077/64k.mp3"));
        list.add(new Station("兴宁电台", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/20500218/64k.mp3"));
        list.add(new Station("兵团七师胡杨河新闻综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500095/64k.mp3"));
        list.add(new Station("兵团二师铁门关综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20211703/64k.mp3"));
        list.add(new Station("兵团八师石河子新闻综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500118/64k.mp3"));
        list.add(new Station("兵团四师可克达拉综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500054/64k.mp3"));
        list.add(new Station("内蒙古交通之声", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1884/64k.mp3"));
        list.add(new Station("内蒙古农村牧区广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1888/64k.mp3"));
        list.add(new Station("内蒙古新闻综合广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1883/64k.mp3"));
        list.add(new Station("内蒙古蒙语广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1882/64k.mp3"));
        list.add(new Station("内蒙古音乐之声", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1886/64k.mp3"));
        list.add(new Station("凌源综合广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/15318298/64k.mp3"));
        list.add(new Station("利津县融媒体中心综合广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20500138/64k.mp3"));
        list.add(new Station("动听913（宣化区融媒体中心综合广播）", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/15318538/64k.mp3"));
        list.add(new Station("动感调频FM94.3 沙湾人民广播电台综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500064/64k.mp3"));
        list.add(new Station("包头城乡广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1892/64k.mp3"));
        list.add(new Station("北碚综合广播·重庆嘉陵之声FM88.7", "HLS", "重庆",
                "https://lhttp-hw.qtfm.cn/live/20211692/64k.mp3"));
        list.add(new Station("北部湾之声 The Voice of Beibu Gulf", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1757/64k.mp3"));
        list.add(new Station("南京音乐广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/4963/64k.mp3"));
        list.add(new Station("南安市广播电视台综合广播", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/5021731/64k.mp3"));
        list.add(new Station("南川融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318405/64k.mp3"));
        list.add(new Station("南平综合广播", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/5022065/64k.mp3"));
        list.add(new Station("卫辉综合广播 动听925", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20500152/64k.mp3"));
        list.add(new Station("历城区融媒体中心综合广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20500194/64k.mp3"));
        list.add(new Station("叙州综合广播 汽车音乐广播FM94.2", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/20500137/64k.mp3"));
        list.add(new Station("合肥交通信息广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/1960/64k.mp3"));
        list.add(new Station("合肥文旅广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/1961/64k.mp3"));
        list.add(new Station("合肥文艺广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/1975/64k.mp3"));
        list.add(new Station("吕梁交通广播", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/4899/64k.mp3"));
        list.add(new Station("吕梁综合广播", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/4020/64k.mp3"));
        list.add(new Station("周口交通广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/15318700/64k.mp3"));
        list.add(new Station("呼和浩特交通广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/5021545/64k.mp3"));
        list.add(new Station("呼和浩特综合广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/5021543/64k.mp3"));
        list.add(new Station("咸宁交通音乐广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/5068/64k.mp3"));
        list.add(new Station("咸宁综合广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/5067/64k.mp3"));
        list.add(new Station("哈尔滨冰城融媒体电台", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/20212259/64k.mp3"));
        list.add(new Station("商丘综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022443/64k.mp3"));
        list.add(new Station("嘉兴交通经济广播 FM92.2", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/1135/64k.mp3"));
        list.add(new Station("嘉兴对农广播 FM88.2", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/1136/64k.mp3"));
        list.add(new Station("嘉兴综合广播 FN104.1", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/1154/64k.mp3"));
        list.add(new Station("四平交通文艺广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/5022465/64k.mp3"));
        list.add(new Station("四平综合广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/15318197/64k.mp3"));
        list.add(new Station("固安县融媒体中心综合广播 FM107.9", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500225/64k.mp3"));
        list.add(new Station("固安综合广播 1079音乐有话说", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20211619/64k.mp3"));
        list.add(new Station("城阳综合广播 青岛广播爱车940", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/5022537/64k.mp3"));
        list.add(new Station("声音控电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318519/64k.mp3"));
        list.add(new Station("大兴安岭综合广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/20500057/64k.mp3"));
        list.add(new Station("大理市电台苍洱调频", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/1940/64k.mp3"));
        list.add(new Station("大理综合广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/20207747/64k.mp3"));
        list.add(new Station("大足综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211676/64k.mp3"));
        list.add(new Station("大连交通广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/3997/64k.mp3"));
        list.add(new Station("大连少儿广播 FM106.7", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/1084/64k.mp3"));
        list.add(new Station("大连新闻综合广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/1089/64k.mp3"));
        list.add(new Station("大连普兰店广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/20212414/64k.mp3"));
        list.add(new Station("天津新闻广播", "HLS", "天津",
                "https://lhttp-hw.qtfm.cn/live/5022134/64k.mp3"));
        list.add(new Station("天津经济广播", "HLS", "天津",
                "https://lhttp-hw.qtfm.cn/live/15318227/64k.mp3"));
        list.add(new Station("天长综合频率", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/4854/64k.mp3"));
        list.add(new Station("太仓综合广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20207759/64k.mp3"));
        list.add(new Station("太原经济广播", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/4018/64k.mp3"));
        list.add(new Station("太和广播电视台 FM104.3", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/15318579/64k.mp3"));
        list.add(new Station("威宁综合广播", "HLS", "贵州",
                "https://lhttp-hw.qtfm.cn/live/5022342/64k.mp3"));
        list.add(new Station("威海交通广播FM102.2 FM95.0 AM1557", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20671/64k.mp3"));
        list.add(new Station("威海综合广播FM105.1 FM107.3 AM1206", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20669/64k.mp3"));
        list.add(new Station("威海音乐广播FM90.7 FM88.3", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/15318612/64k.mp3"));
        list.add(new Station("威远综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/20500102/64k.mp3"));
        list.add(new Station("孝感交通音乐广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/5022063/64k.mp3"));
        list.add(new Station("孝感综合广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/5022064/64k.mp3"));
        list.add(new Station("孝昌964电台", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/15318546/64k.mp3"));
        list.add(new Station("宁海新闻综合广播 FM98.9", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/5022406/64k.mp3"));
        list.add(new Station("安康交通旅游音乐广播", "HLS", "陕西",
                "https://lhttp-hw.qtfm.cn/live/5021862/64k.mp3"));
        list.add(new Station("安阳县Top Radio 88.1", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20209339/64k.mp3"));
        list.add(new Station("安阳市广播电视台交通广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/2138/64k.mp3"));
        list.add(new Station("安阳综合新闻广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/15318224/64k.mp3"));
        list.add(new Station("安阳音乐生活广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/2123/64k.mp3"));
        list.add(new Station("定州融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211638/64k.mp3"));
        list.add(new Station("宝鸡交通旅游广播", "HLS", "陕西",
                "https://lhttp-hw.qtfm.cn/live/15318128/64k.mp3"));
        list.add(new Station("宝鸡综合广播", "HLS", "陕西",
                "https://lhttp-hw.qtfm.cn/live/15318125/64k.mp3"));
        list.add(new Station("宣城交通文艺广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/5023/64k.mp3"));
        list.add(new Station("富顺综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5022355/64k.mp3"));
        list.add(new Station("尤溪综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022498/64k.mp3"));
        list.add(new Station("山东维语都市广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20211680/64k.mp3"));
        list.add(new Station("岳阳县综合广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/20500129/64k.mp3"));
        list.add(new Station("岳阳经济广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/5022391/64k.mp3"));
        list.add(new Station("崇礼综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500100/64k.mp3"));
        list.add(new Station("巧家新闻综合广播 白鹤之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211704/64k.mp3"));
        list.add(new Station("巴音郭楞汉语综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/5022108/64k.mp3?"));
        list.add(new Station("常州音乐广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/2799/64k.mp3"));
        list.add(new Station("常熟市融媒体中心综合广播 声动1008", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/2792/64k.mp3"));
        list.add(new Station("广西交通广播", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1758/64k.mp3"));
        list.add(new Station("广西教育广播 私家车930", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1756/64k.mp3"));
        list.add(new Station("广西文艺广播 FM950广西音乐台", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/4875/64k.mp3"));
        list.add(new Station("广西经济广播 970女主播电台", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1754/64k.mp3"));
        list.add(new Station("广西综合广播 新闻910", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1753/64k.mp3"));
        list.add(new Station("广饶广播电视台", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500036/64k.mp3"));
        list.add(new Station("庆云县融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022403/64k.mp3"));
        list.add(new Station("应城市融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500227/64k.mp3"));
        list.add(new Station("延吉交通之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318331/64k.mp3"));
        list.add(new Station("延边旅游广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/5022438/64k.mp3"));
        list.add(new Station("延边朝鲜语新闻综合广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/20324/64k.mp3"));
        list.add(new Station("开封综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022653/64k.mp3"));
        list.add(new Station("开平电台 飞扬956", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/5037/64k.mp3"));
        list.add(new Station("张家口综合广播", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/15318285/64k.mp3"));
        list.add(new Station("彬州市人民广播电台 mp3", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500035/64k.mp3"));
        list.add(new Station("徐州农村广播（蜻蜓FM）", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20211623/64k.mp3"));
        list.add(new Station("德阳经济生活广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5022110/64k.mp3"));
        list.add(new Station("德阳综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/4987/64k.mp3"));
        list.add(new Station("怀化交通文艺广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/5022070/64k.mp3"));
        list.add(new Station("怀化综合广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/5022069/64k.mp3"));
        list.add(new Station("怀远人民广播电台 FM95.1", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/5021993/64k.mp3"));
        list.add(new Station("成安综合广播久久金曲 FM99.9", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20211694/64k.mp3"));
        list.add(new Station("成武综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211637/64k.mp3"));
        list.add(new Station("成都文化休闲广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/4892/64k.mp3"));
        list.add(new Station("成都新闻广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/4897/64k.mp3"));
        list.add(new Station("成都经济广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/1121/64k.mp3"));
        list.add(new Station("扬州江都广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/5022636/64k.mp3"));
        list.add(new Station("扬州邗江广播FM96.7", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20211632/64k.mp3"));
        list.add(new Station("抗大之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500096/64k.mp3"));
        list.add(new Station("抚州交通音乐广播", "HLS", "江西",
                "https://lhttp-hw.qtfm.cn/live/20500015/64k.mp3"));
        list.add(new Station("抚州新闻综合广播", "HLS", "江西",
                "https://lhttp-hw.qtfm.cn/live/20500226/64k.mp3"));
        list.add(new Station("拉萨综合广播", "HLS", "西藏",
                "https://lhttp-hw.qtfm.cn/live/5022138/64k.mp3"));
        list.add(new Station("新余经济交通广播", "HLS", "江西",
                "https://lhttp-hw.qtfm.cn/live/20093/64k.mp3"));
        list.add(new Station("新密综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500144/64k.mp3"));
        list.add(new Station("新疆汉语新闻广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/1902/64k.mp3"));
        list.add(new Station("新都区广播电视台综合广播 新声905", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500221/64k.mp3"));
        list.add(new Station("新野综合广播 FM89.8", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20500242/64k.mp3"));
        list.add(new Station("无棣广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/5022198/64k.mp3"));
        list.add(new Station("日照交通生活广播 RZBC-2", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/4005/64k.mp3"));
        list.add(new Station("昆明老年广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/1937/64k.mp3"));
        list.add(new Station("星空电台 STAR RADIO", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022379/64k.mp3"));
        list.add(new Station("曲阳融媒FM90.4", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500219/64k.mp3"));
        list.add(new Station("望城综合广播 长沙925电台", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/5022076/64k.mp3"));
        list.add(new Station("杭州城市资讯广播 FM90.7杭州潮流音乐电台", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/15318146/64k.mp3"));
        list.add(new Station("松原交通文艺广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/20212256/64k.mp3"));
        list.add(new Station("松原新闻综合广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/5079/64k.mp3"));
        list.add(new Station("枣强综合广播 年代995", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500202/64k.mp3"));
        list.add(new Station("柳州交通广播", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/20571/64k.mp3"));
        list.add(new Station("柳州综合广播", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/21043/64k.mp3"));
        list.add(new Station("株洲交通广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/3971/64k.mp3"));
        list.add(new Station("桂林旅游音乐广播", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1760/64k.mp3"));
        list.add(new Station("桂林综合广播", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/1759/64k.mp3"));
        list.add(new Station("桓仁电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022699/64k.mp3"));
        list.add(new Station("梁山广播电视台综合广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20500235/64k.mp3"));
        list.add(new Station("梁平融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211646/64k.mp3"));
        list.add(new Station("梅州综合广播", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/1257/64k.mp3"));
        list.add(new Station("楚雄综合广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/4030/64k.mp3"));
        list.add(new Station("武汉经济广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/20200/64k.mp3"));
        list.add(new Station("毕节交通音乐广播", "HLS", "贵州",
                "https://lhttp-hw.qtfm.cn/live/5022712/64k.mp3"));
        list.add(new Station("江夏综合广播 魅力FM1064城市生活音乐广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/5022716/64k.mp3"));
        list.add(new Station("江油综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20673/64k.mp3"));
        list.add(new Station("江门新会电台 mp3", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/5061/64k.mp3"));
        list.add(new Station("江门旅游之声", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/1283/64k.mp3"));
        list.add(new Station("江门综合广播", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/1282/64k.mp3"));
        list.add(new Station("江陵广播电视台 综合广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/20500203/64k.mp3"));
        list.add(new Station("沧州综合广播", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/5021901/64k.mp3"));
        list.add(new Station("泸州对农经济生活广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5021565/64k.mp3"));
        list.add(new Station("泸州综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/5021557/64k.mp3"));
        list.add(new Station("泽州广播电视台综合广播", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/5021761/64k.mp3"));
        list.add(new Station("洛阳文艺广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20211321/64k.mp3"));
        list.add(new Station("济宁生活广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/4008/64k.mp3"));
        list.add(new Station("浦东综合广播 东上海之声FM106.5", "HLS", "上海",
                "https://lhttp-hw.qtfm.cn/live/21355/64k.mp3"));
        list.add(new Station("浦江人民广播电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5021924/64k.mp3"));
        list.add(new Station("海口音乐广播", "HLS", "海南",
                "https://lhttp-hw.qtfm.cn/live/20010/64k.mp3"));
        list.add(new Station("海门新闻综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022640/64k.mp3"));
        list.add(new Station("涞水县流行音乐广播999正青春", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211620/64k.mp3"));
        list.add(new Station("淄博综合广播（FM89）", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/1678/64k.mp3"));
        list.add(new Station("淮北交通广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/20211647/64k.mp3"));
        list.add(new Station("淮安区综合广播 淮安经典992", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/15318398/64k.mp3"));
        list.add(new Station("淮阴区FM100.6淮安车生活", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/5021970/64k.mp3"));
        list.add(new Station("深州综合频率FM106.9", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500238/64k.mp3"));
        list.add(new Station("渭南交通广播", "HLS", "陕西",
                "https://lhttp-hw.qtfm.cn/live/5022389/64k.mp3"));
        list.add(new Station("湖南音乐之声广播 芒果音乐台", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/4979/64k.mp3"));
        list.add(new Station("湛江廉江广播", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/20211578/64k.mp3"));
        list.add(new Station("湛江综合广播", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/20617/64k.mp3"));
        list.add(new Station("滨州交通音乐广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20519/64k.mp3"));
        list.add(new Station("滨州文艺广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/21341/64k.mp3"));
        list.add(new Station("滨州综合广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/5021395/64k.mp3"));
        list.add(new Station("漯河交通广播mp3", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022452/64k.mp3"));
        list.add(new Station("漯河综合广播mp3", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022660/64k.mp3"));
        list.add(new Station("漳浦综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022658/64k.mp3"));
        list.add(new Station("潍坊音乐文旅广播", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/15318631/64k.mp3"));
        list.add(new Station("澄海电台FM100.5", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/5022439/64k.mp3"));
        list.add(new Station("澎湃907   江阴人民广播电台", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/2789/64k.mp3"));
        list.add(new Station("濉溪综合广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/20500240/64k.mp3"));
        list.add(new Station("濮阳县FM1053快乐调频", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20206/64k.mp3"));
        list.add(new Station("焦作新闻综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022557/64k.mp3"));
        list.add(new Station("玉环综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20212390/64k.mp3"));
        list.add(new Station("甘肃人民广播电台农村广播", "HLS", "甘肃",
                "https://lhttp-hw.qtfm.cn/live/3941/64k.mp3"));
        list.add(new Station("益阳交通广播", "HLS", "湖南",
                "https://lhttp-hw.qtfm.cn/live/15318153/64k.mp3"));
        list.add(new Station("盐城交通广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20326/64k.mp3?"));
        list.add(new Station("盐城滨海广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20207779/64k.mp3"));
        list.add(new Station("盱眙综合广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/20500051/64k.mp3"));
        list.add(new Station("眉山综合广播", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/4027/64k.mp3"));
        list.add(new Station("石嘴山综合广播", "HLS", "宁夏",
                "https://lhttp-hw.qtfm.cn/live/5022563/64k.mp3"));
        list.add(new Station("磁县融媒综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500116/64k.mp3"));
        list.add(new Station("福建海峡之声", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/1744/64k.mp3"));
        list.add(new Station("秦皇岛体育广播", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20835/64k.mp3"));
        list.add(new Station("第一师阿拉尔人民广播电台新闻综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500209/64k.mp3"));
        list.add(new Station("红河综合广播", "HLS", "云南",
                "https://lhttp-hw.qtfm.cn/live/4033/64k.mp3"));
        list.add(new Station("肇庆高新之声", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/20500213/64k.mp3"));
        list.add(new Station("肥乡广播电视台音乐广播", "HLS", "河北",
                "https://lhttp-hw.qtfm.cn/live/20500104/64k.mp3"));
        list.add(new Station("自强之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5021905/64k.mp3"));
        list.add(new Station("芜湖交通经济广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/5027/64k.mp3"));
        list.add(new Station("芜湖综合广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/5029/64k.mp3"));
        list.add(new Station("苍溪人民广播电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500228/64k.mp3"));
        list.add(new Station("荆门综合广播", "HLS", "湖北",
                "https://lhttp-hw.qtfm.cn/live/20211577/64k.mp3"));
        list.add(new Station("蓬安综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500206/64k.mp3"));
        list.add(new Station("蓬莱电台仙境之声", "HLS", "山东",
                "https://lhttp-hw.qtfm.cn/live/20500112/64k.mp3"));
        list.add(new Station("蚌埠交通文艺广播 FM98.4", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/4577/64k.mp3"));
        list.add(new Station("蚌埠综合广播 FM107.9 AM765", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/20154/64k.mp3"));
        list.add(new Station("襄州综合广播 都市965汽车音乐广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500108/64k.mp3"));
        list.add(new Station("西江之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022379/64k.mp3?"));
        list.add(new Station("诏安广播电视台综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500186/64k.mp3"));
        list.add(new Station("贵港综合广播/贵港金曲1019/FM101.9", "HLS", "广西",
                "https://lhttp-hw.qtfm.cn/live/20697/64k.mp3"));
        list.add(new Station("赣榆综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500216/64k.mp3"));
        list.add(new Station("赤峰交通广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1899/64k.mp3"));
        list.add(new Station("赤峰农村牧区广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1898/64k.mp3"));
        list.add(new Station("赤峰综合广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1896/64k.mp3"));
        list.add(new Station("赤峰蒙语广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/1897/64k.mp3"));
        list.add(new Station("轮台之声", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500099/64k.mp3"));
        list.add(new Station("辉南综合广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/20211566/64k.mp3"));
        list.add(new Station("辽宁经典音乐广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/20021/64k.mp3"));
        list.add(new Station("辽宁经济广播", "HLS", "辽宁",
                "https://lhttp-hw.qtfm.cn/live/20019/64k.mp3"));
        list.add(new Station("运城文艺广播", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/1191/64k.mp3"));
        list.add(new Station("运城金荔枝经典流行音乐广播电台", "HLS", "山西",
                "https://lhttp-hw.qtfm.cn/live/15318194/64k.mp3"));
        list.add(new Station("通化交通文艺广播", "HLS", "吉林",
                "https://lhttp-hw.qtfm.cn/live/20500120/64k.mp3"));
        list.add(new Station("通许融媒广播电台", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/20500157/64k.mp3"));
        list.add(new Station("郏县综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022022/64k.mp3"));
        list.add(new Station("郑州经典音乐广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/1223/64k.mp3"));
        list.add(new Station("郫都综合广播·川味965", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/20500159/64k.mp3"));
        list.add(new Station("金堂综合广播FM88.9 成都年代音乐怀旧好声音", "HLS", "四川",
                "https://lhttp-hw.qtfm.cn/live/20500160/64k.mp3"));
        list.add(new Station("铜山综合广播 徐州经典音乐FM942", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/15318160/64k.mp3"));
        list.add(new Station("铜陵交通生活广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/21305/64k.mp3"));
        list.add(new Station("镇江交通广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/3985/64k.mp3"));
        list.add(new Station("镇江文艺广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/4605/64k.mp3"));
        list.add(new Station("镇江综合广播", "HLS", "江苏",
                "https://lhttp-hw.qtfm.cn/live/3984/64k.mp3"));
        list.add(new Station("镇海104.7 Nice FM", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/20033/64k.mp3"));
        list.add(new Station("镇雄新闻综合广播", "HLS", "贵州",
                "https://lhttp-hw.qtfm.cn/live/20210752/64k.mp3"));
        list.add(new Station("长垣广播电视台综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318663/64k.mp3"));
        list.add(new Station("长江水上安全信息台·长江之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5021868/64k.mp3"));
        list.add(new Station("闽侯综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500236/64k.mp3"));
        list.add(new Station("阆中综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500020/64k.mp3"));
        list.add(new Station("阜宁县融媒体中心 新闻综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20207753/64k.mp3"));
        list.add(new Station("阿克苏市融媒体中心综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/20500041/64k.mp3"));
        list.add(new Station("阿克苏维语综合广播", "HLS", "新疆",
                "https://lhttp-hw.qtfm.cn/live/15318551/64k.mp3"));
        list.add(new Station("阿坝安多藏语综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500234/64k.mp3"));
        list.add(new Station("阿基米德-健康电台", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500232/64k.mp3"));
        list.add(new Station("阿拉善汉语综合广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/5022521/64k.mp3?"));
        list.add(new Station("阿拉善蒙语综合广播", "HLS", "内蒙古",
                "https://lhttp-hw.qtfm.cn/live/5022555/64k.mp3"));
        list.add(new Station("陕西经济广播·唐诗电台", "HLS", "陕西",
                "https://lhttp-hw.qtfm.cn/live/1603/64k.mp3"));
        list.add(new Station("霸州市融媒体中心综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20211658/64k.mp3"));
        list.add(new Station("青海交通音乐卫星广播", "HLS", "青海",
                "https://lhttp-hw.qtfm.cn/live/5009/64k.mp3"));
        list.add(new Station("青海新闻综合广播", "HLS", "青海",
                "https://lhttp-hw.qtfm.cn/live/20063/64k.mp3"));
        list.add(new Station("青海经济广播", "HLS", "青海",
                "https://lhttp-hw.qtfm.cn/live/5008/64k.mp3"));
        list.add(new Station("韩城综合广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318413/64k.mp3"));
        list.add(new Station("韶关综合广播", "HLS", "广东",
                "https://lhttp-hw.qtfm.cn/live/5022074/64k.mp3"));
        list.add(new Station("项城936 项城综艺交通广播", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/15318335/64k.mp3"));
        list.add(new Station("项城广播-国风国潮105.9", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20210757/64k.mp3"));
        list.add(new Station("颍上广播电视台FM96.2", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/20500039/64k.mp3"));
        list.add(new Station("驻马店综合广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022118/64k.mp3"));
        list.add(new Station("高安电台 最爱942", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/20500014/64k.mp3"));
        list.add(new Station("鸡西交通广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/20500087/64k.mp3"));
        list.add(new Station("鸡西综合广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/20500089/64k.mp3"));
        list.add(new Station("鹤壁交通广播", "HLS", "河南",
                "https://lhttp-hw.qtfm.cn/live/5022089/64k.mp3"));
        list.add(new Station("黄山旅游广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/1969/64k.mp3"));
        list.add(new Station("黄山综合广播", "HLS", "安徽",
                "https://lhttp-hw.qtfm.cn/live/1968/64k.mp3"));
        list.add(new Station("黄岩电台", "HLS", "浙江",
                "https://lhttp-hw.qtfm.cn/live/5022671/64k.mp3"));
        list.add(new Station("黄梅之声", "HLS", "地方",
                "https://lhttp-hw.qtfm.cn/live/5022280/64k.mp3"));
        list.add(new Station("黑龙江交通广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/4973/64k.mp3"));
        list.add(new Station("黑龙江老年·少儿广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/4972/64k.mp3"));
        list.add(new Station("黑龙江都市·女性广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/4968/64k.mp3"));
        list.add(new Station("黑龙江高校广播", "HLS", "黑龙江",
                "https://lhttp-hw.qtfm.cn/live/4976/64k.mp3"));
        list.add(new Station("龙岩综合广播", "HLS", "福建",
                "https://lhttp-hw.qtfm.cn/live/20709/64k.mp3"));
        list.add(new Station("龙游人民广播电台FM95.4", "HLS", "浙江",
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
        list.add(new Station("CNR-8 民族之声", "HLS", "中央",
                "https://satellitepull.cnr.cn/live/wxmzzs/playlist.m3u8"));
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
        list.add(new Station("东方卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/dongfang_2.m3u8"));
        list.add(new Station("云南卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/yunnan_2.m3u8"));
        list.add(new Station("内蒙古卫视", "HLS", "中央",
                "https://piccpndali.v.myalicdn.com/audio/neimenggu_2.m3u8"));
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

        return list;
    }

    /**
     * 已确认不可用的源 —— 保留下来避免以后有人再试一遍。UI 不展示。
     *
     * 注意：这里**不含蜻蜓系**。蜻蜓是好的，详见类注释里的 UA 黑名单说明。
     */
    public static String[] knownDeadNotes() {
        return new String[]{
            "CNR 央广 ngcdn00X.cnr.cn: 仅 001/002 存活且为 HLS —— 官方源改用 satellitepull.cnr.cn（见「央广·卫视」分类）",
            "中国国际广播电台 / 各地方台官网流: 多为 HLS 或需要 Referer",
            "江苏新闻广播 (lzlive.vojs.cn): 返回 206 但只有 342 字节，实际无音频",
            "提醒：判断源是否存活时不要带 WinampMPEG 的 User-Agent —— 蜻蜓会 403，会误判",
            "提醒：不要带 curl 默认 UA 探测 —— zeno.fm 对默认 UA 返 401，用 Stagefright 才 200",
            "提醒：不要用 16 路并发测速 —— 会把本机出口打爆，得到「全部失败」的假象（实测踩过两次）",
        };
    }
}
