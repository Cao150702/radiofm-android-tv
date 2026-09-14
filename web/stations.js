/* 内置电台表 —— 与 Android 版 StationData.java 一一对应
 *
 * 重要：这里的每一条都在 2026-09-14 实测过 HTTP 200 且能拉到真实音频。
 *
 * ── 关于蜻蜓FM（别再重复踩坑）──
 * 曾判定「蜻蜓系全死」，那个结论是错的。蜻蜓有 **User-Agent 黑名单**：
 * curl / Mozilla / VLC / Stagefright / ExoPlayer 全部 200，**只有 WinampMPEG 返回 403**。
 * 当初探测脚本为了取 ICY 元数据带了 Winamp 的 UA，于是把所有蜻蜓地址误判成失效。
 * （本文件维护时如果用脚本探测，千万别带 WinampMPEG 的 UA。）
 *
 * ── httpsOk 字段 ── 该流是否支持 https（2026-09-14 实测）：
 *   true  → https 直连可用，web 版只走 https
 *   false → 纯 HTTP，web 版跳过 https 尝试，省掉 3.6 秒无效重试
 *
 * 每条支持多个备用地址，第一个失败会自动试下一个。
 */

const BUILT_IN = [

  /* ---------- 大陆 · 中央台 ---------- */
  { name: '中国之声 CNR-1',          genre: '新闻', region: '中央',
    urls: ['https://lhttp.qtfm.cn/live/15318317/64k.mp3'], httpsOk:true },

  /* ---------- 大陆 · 北京 ---------- */
  { name: '北京新闻广播',                  genre: '新闻', region: '北京',
    urls: ['https://lhttp.qtfm.cn/live/339/64k.mp3'], httpsOk:true },
  { name: '北京交通广播',                  genre: '交通', region: '北京',
    urls: ['https://lhttp.qingting.fm/live/336/64k.mp3'], httpsOk:true },
  { name: '北京文艺广播',                  genre: '文艺', region: '北京',
    urls: ['https://lhttp.qtfm.cn/live/333/64k.mp3'], httpsOk:true },
  { name: '北京音乐广播',                  genre: '音乐', region: '北京',
    urls: ['https://lhttp.qtfm.cn/live/332/64k.mp3'], httpsOk:true },

  /* ---------- 大陆 · 上海 ---------- */
  { name: '上海新闻广播',                  genre: '新闻', region: '上海',
    urls: ['http://lhttp.qingting.fm/live/270/64k.mp3'], httpsOk:true },
  { name: '上海东广新闻台',                genre: '新闻', region: '上海',
    urls: ['http://lhttp.qingting.fm/live/275/64k.mp3'], httpsOk:true },
  { name: '上海动感101',                genre: '流行', region: '上海',
    urls: ['https://lhttp.qingting.fm/live/274/64k.mp3'], httpsOk:true },
  { name: '上海经典音乐广播',              genre: '经典', region: '上海',
    urls: ['http://lhttp.qingting.fm/live/267/64k.mp3'], httpsOk:true },
  { name: '上海音乐广播',                  genre: '音乐', region: '上海',
    urls: ['http://lhttp.qingting.fm/live/273/64k.mp3'], httpsOk:true },

  /* ---------- 大陆 · 广东 ---------- */
  { name: '广东新闻广播',                  genre: '新闻', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/1254/64k.mp3'], httpsOk:true },
  { name: '广东珠江经济台',                genre: '经济', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/1259/64k.mp3'], httpsOk:true },
  { name: '广东音乐之声',                  genre: '音乐', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/1260/64k.mp3'], httpsOk:true },
  { name: '广东交通之声',                  genre: '交通', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/1262/64k.mp3'], httpsOk:true },
  { name: '广东股市广播',                  genre: '财经', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/4847/64k.mp3'], httpsOk:true },
  { name: '广东城市之声',                  genre: '综合', region: '广东',
    urls: ['https://lhttp.qtfm.cn/live/469/64k.mp3'], httpsOk:true },
  { name: '深圳新闻广播',                  genre: '新闻', region: '深圳',
    urls: ['http://lhttp.qingting.fm/live/1270/64k.mp3'], httpsOk:true },
  { name: '广州金曲音乐广播',              genre: '音乐', region: '广州',
    urls: ['http://lhttp.qingting.fm/live/20192/64k.mp3'], httpsOk:true },
  { name: '广州新闻资讯广播',              genre: '新闻', region: '广州',
    urls: ['http://lhttp.qingting.fm/live/4848/64k.mp3'], httpsOk:true },
  { name: '顺德音乐之声',                  genre: '音乐', region: '佛山',
    urls: ['https://lhttp.qtfm.cn/live/20500150/64k.mp3'], httpsOk:true },

  /* ---------- 大陆 · 其他省市 ---------- */
  { name: '四川新闻广播',                  genre: '新闻', region: '四川',
    urls: ['https://lhttp.qtfm.cn/live/4906/64k.mp3'], httpsOk:true },
  { name: '江苏经典流行音乐广播',          genre: '经典', region: '江苏',
    urls: ['https://lhttp.qtfm.cn/live/4938/64k.mp3'], httpsOk:true },
  { name: '河南星河音乐广播',              genre: '音乐', region: '河南',
    urls: ['http://lhttp.qingting.fm/live/20210755/64k.mp3'], httpsOk:true },
  { name: '郑州新闻广播',                  genre: '新闻', region: '河南',
    urls: ['http://lhttp.qingting.fm/live/1220/64k.mp3'], httpsOk:true },
  { name: '济南故事广播',                  genre: '故事', region: '山东',
    urls: ['https://lhttp.qtfm.cn/live/1672/64k.mp3'], httpsOk:true },
  { name: '安徽小说评书广播',              genre: '评书', region: '安徽',
    urls: ['https://lhttp.qtfm.cn/live/1951/64k.mp3'], httpsOk:true },
  { name: '长沙 BIG RADIO 流行音乐',genre: '流行', region: '湖南',
    urls: ['http://lhttp.qingting.fm/live/20847/64k.mp3'], httpsOk:true },

  /* ---------- 大陆 · 网络台 ---------- */
  { name: 'CityFM 城市音乐台',      genre: '音乐', region: '网络',
    urls: ['https://lhttp.qtfm.cn/live/20500153/64k.mp3'], httpsOk:true },
  { name: 'MY FM 全国音乐频道',      genre: '音乐', region: '网络',
    urls: ['http://lhttp.qingting.fm/live/20194/64k.mp3'], httpsOk:true },
  { name: '雨声轻音乐',                    genre: '轻音乐', region: '网络',
    urls: ['https://stream.zeno.fm/689zc32y4x8uv'], httpsOk:true },
  { name: '德云社相声合集',                genre: '相声', region: '网络',
    urls: ['https://stream.zeno.fm/yqawwmweq8mtv'], httpsOk:true },
  { name: 'BBN 中文',                  genre: '宗教', region: '网络',
    urls: ['https://streams.radiomast.io/ce298b32-8776-4192-9900-092f44b63e7f'], httpsOk:true },
  { name: 'AsiaFM 高清音乐台',      genre: '音乐', region: '网络',
    urls: ['http://asiafm.hk:8000/asiahd'], httpsOk:false },
  { name: 'AsiaFM 亚洲经典台',      genre: '经典', region: '网络',
    urls: ['http://goldfm.cn:8000/goldfm'], httpsOk:false },
  { name: 'AsiaFM 亚洲热歌台',      genre: '流行', region: '网络',
    urls: ['http://hot.asiafm.net:8000/asiafm'], httpsOk:false },
  { name: 'AsiaFM 亚洲天空台',      genre: '流行', region: '网络',
    urls: ['http://funradio.cn:8000/funradio'], httpsOk:false },
  { name: 'AsiaFM 亚洲粤语台',      genre: '粤语', region: '网络',
    urls: ['http://yyt.asiafm.net:8000/asiafm'], httpsOk:false },
  { name: 'Big B Radio 亚洲音乐台',genre: '亚洲流行', region: '网络',
    urls: ['https://antares.dribbcast.com/proxy/apop?mp=/s', 'https://antares.dribbcast.com/proxy/cpop?mp=/s'], httpsOk:true },
  { name: 'Chinese Music World 华语音乐',genre: '华语', region: '网络',
    urls: ['https://radio.chinesemusicworld.com/chinesemusic.mp3'], httpsOk:true },
  { name: 'Acast 华语电台',          genre: '综合', region: '网络',
    urls: ['https://acast01.kolorboxlab.com/radio/8010/radio.mp3'], httpsOk:true },

  /* ---------- 港台 ---------- */
  { name: '香港电台 RTHK Radio 1',genre: '综合', region: '香港',
    urls: ['http://stm.rthk.hk/radio1'], httpsOk:true },
  { name: '香港电台 RTHK Radio 2',genre: '综合', region: '香港',
    urls: ['http://stm.rthk.hk/radio2'], httpsOk:true },
  { name: '香港电台 RTHK Radio 3',genre: '英文', region: '香港',
    urls: ['http://stm.rthk.hk/radio3'], httpsOk:true },
  { name: '香港电台 RTHK Radio 4',genre: '古典', region: '香港',
    urls: ['http://stm.rthk.hk/radio4'], httpsOk:true },
  { name: '香港电台 RTHK Radio 5',genre: '粤语', region: '香港',
    urls: ['http://stm1.rthk.hk/radio5'], httpsOk:true },
  { name: '香港电台 RTHK 普通话台',  genre: '普通话', region: '香港',
    urls: ['http://stm.rthk.hk/radiopth'], httpsOk:true },
  { name: '香港国际机场塔台 VHHH',    genre: '航空', region: '香港',
    urls: ['https://s1-fmt2.liveatc.net/vhhh5'], httpsOk:true },

  /* ---------- 音乐 / 国际华语 ---------- */
  { name: 'Anison 动漫音乐台',      genre: '动漫', region: '日本',
    urls: ['http://pool.anison.fm:9000/AniSonFM(320)'], httpsOk:true },
  { name: '法国国际广播 RFI 中文',    genre: '新闻', region: '法国',
    urls: ['https://rfienchinois64k.ice.infomaniak.ch/rfienchinois-64.mp3'], httpsOk:true },
  { name: 'Fred Film Radio 中文',genre: '影视', region: '国际',
    urls: ['https://s10.webradio-hosting.com/proxy/fredradiocn/stream'], httpsOk:true },
  { name: 'Radio Maria Chinese',genre: '宗教', region: '国际',
    urls: ['https://onair7.xdevel.com/proxy/xautocloud_nwct_1310?mp=/;'], httpsOk:true },
  { name: 'Lam Rim 藏传佛教电台',  genre: '宗教', region: '国际',
    urls: ['http://199.180.72.2:9097/lamrim'], httpsOk:false },
  { name: 'Swiss News 瑞士新闻',genre: '新闻', region: '瑞士',
    urls: ['https://replaynewszh.ice.infomaniak.ch/replaynewszh-128.mp3'], httpsOk:true },
  { name: 'Curiosity 电台',      genre: '综合', region: '国际',
    urls: ['http://curiosity.shoutca.st:8019/stream'], httpsOk:false },
];

/* 已确认失效但仍常被引用的源，保留为「已知不可用」清单，避免以后有人再试一遍。UI 不展示。
   注意：这里**不含蜻蜓系** —— 蜻蜓是好的，见文件开头说明。 */
const KNOWN_DEAD_NOTES = [
  'CNR 央广自有 CDN (ngcdn00X.cnr.cn): 仅 001/002 存活，且为 HLS，Android 4.4 不支持',
  '中国国际广播电台 / 各地方台官网流: 多为 HLS 或需要 Referer',
  '江苏新闻广播 (lzlive.vojs.cn): 返回 206 但只有 342 字节，实际无音频',
  '排查提醒：探测源时不要带 WinampMPEG 的 User-Agent，蜻蜓会 403 造成误判',
];
