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
 * ── 关于 https 备用地址 ──
 * 蜻蜓系的台普遍能同时走 https 和 http（实测同路径换协议即可）。
 * 这里把 http 版放在**备用位**（第二个），因为 Android 4.4 的坑不同：
 *   · 4.4 系统支持 TLS 1.2 但默认只启用 TLS 1.0，而 MediaPlayer 用的是
 *     **native TLS 栈**，Tls12.java 里那个 HttpsURLConnection 补丁对它无效；
 *   · 所以 https 流在 4.4 上可能握手失败，此时会自动回落到 http 那条。
 * 放在备用位而不是首位，是为了让新系统优先走 https（更安全），
 * 而 4.4 老机器失败后能自动降级 —— 正好对上 RadioService 的「逐条试地址」逻辑。
 *
 * ── 关于 HLS ──
 * 本表已剔除 .m3u8 / HLS 流 —— Android 4.4 的 MediaPlayer 不支持。
 * radio-browser.info 上很多高票中文台是 HLS，取用时必须过滤。
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
                "https://lhttp.qtfm.cn/live/15318317/64k.mp3",
                "http://lhttp.qtfm.cn/live/15318317/64k.mp3"));      // v=9932

        // ================= 大陆 · 北京 =================
        list.add(new Station("北京新闻广播", "新闻", "北京",
                "https://lhttp.qtfm.cn/live/339/64k.mp3",
                "http://lhttp.qtfm.cn/live/339/64k.mp3"));           // v=1198
        list.add(new Station("北京交通广播", "交通", "北京",
                "https://lhttp.qingting.fm/live/336/64k.mp3",
                "http://lhttp.qingting.fm/live/336/64k.mp3"));       // v=637
        list.add(new Station("北京文艺广播", "文艺", "北京",
                "https://lhttp.qtfm.cn/live/333/64k.mp3",
                "http://lhttp.qtfm.cn/live/333/64k.mp3"));           // v=539
        list.add(new Station("北京音乐广播", "音乐", "北京",
                "https://lhttp.qtfm.cn/live/332/64k.mp3",
                "http://lhttp.qtfm.cn/live/332/64k.mp3"));           // v=355

        // ================= 大陆 · 上海 =================
        list.add(new Station("上海新闻广播", "新闻", "上海",
                "http://lhttp.qingting.fm/live/270/64k.mp3"));        // v=2041
        list.add(new Station("上海东广新闻台", "新闻", "上海",
                "http://lhttp.qingting.fm/live/275/64k.mp3"));        // v=375
        list.add(new Station("上海动感101", "流行", "上海",
                "https://lhttp.qingting.fm/live/274/64k.mp3",
                "http://lhttp.qingting.fm/live/274/64k.mp3"));       // v=774
        list.add(new Station("上海经典音乐广播", "经典", "上海",
                "http://lhttp.qingting.fm/live/267/64k.mp3"));        // v=531
        list.add(new Station("上海音乐广播", "音乐", "上海",
                "http://lhttp.qingting.fm/live/273/64k.mp3"));        // v=503

        // ================= 大陆 · 广东 =================
        list.add(new Station("广东新闻广播", "新闻", "广东",
                "https://lhttp.qtfm.cn/live/1254/64k.mp3",
                "http://lhttp.qtfm.cn/live/1254/64k.mp3"));          // v=1225
        list.add(new Station("广东珠江经济台", "经济", "广东",
                "https://lhttp.qtfm.cn/live/1259/64k.mp3",
                "http://lhttp.qtfm.cn/live/1259/64k.mp3"));          // v=1871
        list.add(new Station("广东音乐之声", "音乐", "广东",
                "https://lhttp.qtfm.cn/live/1260/64k.mp3",
                "http://lhttp.qtfm.cn/live/1260/64k.mp3"));          // v=1261
        list.add(new Station("广东交通之声", "交通", "广东",
                "https://lhttp.qtfm.cn/live/1262/64k.mp3",
                "http://lhttp.qtfm.cn/live/1262/64k.mp3"));          // v=992
        list.add(new Station("广东股市广播", "财经", "广东",
                "https://lhttp.qtfm.cn/live/4847/64k.mp3",
                "http://lhttp.qtfm.cn/live/4847/64k.mp3"));          // v=902
        list.add(new Station("广东城市之声", "综合", "广东",
                "https://lhttp.qtfm.cn/live/469/64k.mp3",
                "http://lhttp.qtfm.cn/live/469/64k.mp3"));           // v=515
        list.add(new Station("深圳新闻广播", "新闻", "深圳",
                "http://lhttp.qingting.fm/live/1270/64k.mp3"));       // v=1167
        list.add(new Station("广州金曲音乐广播", "音乐", "广州",
                "http://lhttp.qingting.fm/live/20192/64k.mp3"));      // v=977
        list.add(new Station("广州新闻资讯广播", "新闻", "广州",
                "http://lhttp.qingting.fm/live/4848/64k.mp3"));       // v=559
        list.add(new Station("顺德音乐之声", "音乐", "佛山",
                "https://lhttp.qtfm.cn/live/20500150/64k.mp3",
                "http://lhttp.qtfm.cn/live/20500150/64k.mp3"));      // v=602

        // ================= 大陆 · 其他省市 =================
        list.add(new Station("四川新闻广播", "新闻", "四川",
                "https://lhttp.qtfm.cn/live/4906/64k.mp3",
                "http://lhttp.qtfm.cn/live/4906/64k.mp3"));          // v=421
        list.add(new Station("江苏经典流行音乐广播", "经典", "江苏",
                "https://lhttp.qtfm.cn/live/4938/64k.mp3",
                "http://lhttp.qtfm.cn/live/4938/64k.mp3"));          // v=366
        list.add(new Station("河南星河音乐广播", "音乐", "河南",
                "http://lhttp.qingting.fm/live/20210755/64k.mp3"));   // v=362
        list.add(new Station("郑州新闻广播", "新闻", "河南",
                "http://lhttp.qingting.fm/live/1220/64k.mp3"));       // v=402
        list.add(new Station("济南故事广播", "故事", "山东",
                "https://lhttp.qtfm.cn/live/1672/64k.mp3",
                "http://lhttp.qtfm.cn/live/1672/64k.mp3"));          // v=687
        list.add(new Station("安徽小说评书广播", "评书", "安徽",
                "https://lhttp.qtfm.cn/live/1951/64k.mp3",
                "http://lhttp.qtfm.cn/live/1951/64k.mp3"));          // v=1675
        list.add(new Station("长沙 BIG RADIO 流行音乐", "流行", "湖南",
                "http://lhttp.qingting.fm/live/20847/64k.mp3"));      // v=365

        // ================= 大陆 · 网络台（非蜻蜓源）=================
        list.add(new Station("CityFM 城市音乐台", "音乐", "网络",
                "https://lhttp.qtfm.cn/live/20500153/64k.mp3",
                "http://lhttp.qtfm.cn/live/20500153/64k.mp3"));      // v=569
        list.add(new Station("MY FM 全国音乐频道", "音乐", "网络",
                "http://lhttp.qingting.fm/live/20194/64k.mp3"));      // v=449
        list.add(new Station("雨声轻音乐", "轻音乐", "网络",
                "https://stream.zeno.fm/689zc32y4x8uv",
                "http://stream.zeno.fm/689zc32y4x8uv"));             // v=2488，助眠用
        list.add(new Station("德云社相声合集", "相声", "网络",
                "https://stream.zeno.fm/yqawwmweq8mtv",
                "http://stream.zeno.fm/yqawwmweq8mtv"));             // v=2393
        list.add(new Station("BBN 中文", "宗教", "网络",
                "https://streams.radiomast.io/ce298b32-8776-4192-9900-092f44b63e7f",
                "http://streams.radiomast.io/ce298b32-8776-4192-9900-092f44b63e7f")); // v=1336

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

        // ================= 亚洲调频 AsiaFM =================
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

        // ================= 音乐 / 国际华语 =================
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

        return list;
    }

    /**
     * 已确认不可用的源 —— 保留下来避免以后有人再试一遍。UI 不展示。
     *
     * 注意：这里**不含蜻蜓系**。蜻蜓是好的，详见类注释里的 UA 黑名单说明。
     */
    public static String[] knownDeadNotes() {
        return new String[]{
            "CNR 央广自有 CDN (ngcdn00X.cnr.cn): 仅 001/002 存活，且为 HLS，Android 4.4 不支持",
            "中国国际广播电台 / 各地方台官网流: 多为 HLS 或需要 Referer",
            "江苏新闻广播 (lzlive.vojs.cn): 返回 206 但只有 342 字节，实际无音频",
            "提醒：判断源是否存活时不要带 WinampMPEG 的 User-Agent —— 蜻蜓会 403，会误判",
        };
    }
}
