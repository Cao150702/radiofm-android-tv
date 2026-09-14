/* 网络收音机 · Web 预览版
 *
 * 与 Android 版 RadioService 对齐的状态机：
 *   IDLE(0) → PREPARING(1) → PLAYING(2) / ERROR(3)
 * 一个地址失败 → 同址重试 2 次 → 换下一个备用地址 → 全部失败才报错。
 *
 * Web 与 Android 的三处必要差异：
 *  1) 浏览器不给自动播放：首次必须用户点一下，之后换台才能无缝。
 *  2) 公网隧道是 https 页面，http:// 的流会被「混合内容」拦截。所以每条流按
 *     stations.js 里实测的 httpsOk 决定候选地址（能走 https 就走 https，
 *     已知纯 HTTP 的直接跳过 https 免得白等），回落只在 http 页面下有效。
 *  3) 曲目信息(ICY) 浏览器读不到第三方响应头，默认直连、失败静默降级；
 *     可用 ?icyproxy=<前缀> 指定自建转发。
 */

(function () {
'use strict';

// ================= 常量 =================

const STORAGE_KEY  = 'radio_prefs_v1';
const MAX_RETRY_PER_URL = 2;      // 同址重试次数（对齐 Android 版）
const RETRY_DELAY_MS    = 1200;
const SWITCH_DELAY_MS   = 800;
const ICY_INTERVAL_MS   = 30000;
const ICY_TIMEOUT_MS    = 9000;
const SLEEP_TICK_MS     = 1000;

/* 缓冲超时。
 *
 * 背景：网络电台的源普遍不稳（实测 asiafm.hk 吞吐在 9.8KB~478KB 之间抖），
 * 碰上一直喂不满缓冲的源，<audio> 会永远停在 waiting —— 没有 error 事件，
 * 状态机就卡在「缓冲中」，用户看到的是一个永远转圈、不报错、也不换台的界面。
 * 电视上待机常开，这种"看起来坏了"最要命。
 *
 * 时间取 15s：比正常源的首次缓冲慢，又短到用户还能忍。
 */
const BUFFER_STALL_MS   = 15000;

/* ICY 元数据的取法。
 *
 * 浏览器出于同源策略，读不到第三方流的响应头（拿不到 icy-metaint），所以：
 *  - 本机 localhost 打开：部分流会放行 CORS，直连即可（默认走这条）
 *  - 原生壳子（Capacitor 等）里没有 CORS 限制，直连必然可用
 *  - 公网 https 域名下：需要自建一个带 CORS 头的转发（见 README）
 *
 * 可用 ?icyproxy=<前缀> 临时指定，例如 ?icyproxy=https://my-proxy/?url=
 * 拿不到就静默隐藏曲目信息，不影响播放。
 */
const ICY_PROXY_PARAM = new URLSearchParams(location.search).get('icyproxy') || '';

const STATE = { IDLE: 0, PREPARING: 1, PLAYING: 2, ERROR: 3 };
const STATE_BADGE = { 0: 'IDLE', 1: 'BUFFERING', 2: 'LIVE', 3: 'ERROR' };

// ================= DOM =================

const $ = (id) => document.getElementById(id);
const el = {
  stationName: $('stationName'), stationMeta: $('stationMeta'),
  ticks: $('ticks'), needle: $('needle'),
  statusText: $('statusText'), stateBadge: $('stateBadge'), icyText: $('icyText'),
  btnPlay: $('btnPlay'), btnStop: $('btnStop'), btnFav: $('btnFav'),
  filterBox: $('filterBox'), filterMode: $('filterMode'), countHint: $('countHint'),
  list: $('stationList'), toast: $('toast'),
  mAdd: $('mAdd'), addName: $('addName'), addUrl: $('addUrl'), addSave: $('addSave'),
  mImport: $('mImport'), importText: $('importText'), importDo: $('importDo'),
  mOnline: $('mOnline'), onlineSearch: $('onlineSearch'), onlineCountry: $('onlineCountry'),
  onlineGo: $('onlineGo'), onlineHint: $('onlineHint'), onlineList: $('onlineList'),
  mSettings: $('mSettings'), sleepStatus: $('sleepStatus'),
  alarmEnabled: $('alarmEnabled'), alarmHour: $('alarmHour'), alarmMinute: $('alarmMinute'),
  alarmStationLabel: $('alarmStationLabel'), alarmPick: $('alarmPick'),
  showIcy: $('showIcy'), bootResume: $('bootResume'), settingsSave: $('settingsSave'),
  mPick: $('mPick'), pickList: $('pickList'),
};

// ================= 存储 =================

const DEFAULTS = {
  favorites: [], customStations: [], sleepMinutes: 0, sleepEndsAt: 0,
  alarmEnabled: false, alarmHour: 7, alarmMinute: 0, alarmStation: '', alarmUrl: '',
  showIcy: true, bootResume: false, lastStationUrl: '', lastName: '',
};

let store = Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}'));
const save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(store));

// ================= 工具 =================

function toast(msg, ms) {
  el.toast.textContent = msg;
  el.toast.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.toast.hidden = true; }, ms || 2200);
}

function isSecurePage() {
  // 注意：只有 https: 才算安全上下文。localhost / 127.0.0.1 虽然是「可信来源」，
  // 但浏览器并不会因此拦截 http 子资源 —— 所以这里不能把它们算进来，
  // 否则本地调试时会把所有 HTTP 电台误判成不可播。
  return location.protocol === 'https:';
}
const SECURE = isSecurePage();

/** 把一条 http:// 地址升级成 https://（同主机同路径） */
function httpsOf(url) {
  return /^http:\/\//i.test(url) ? 'https://' + url.slice(7) : url;
}

/** 当前页面协议下，这条地址能不能用 */
function usable(url) {
  if (!SECURE) return true;                 // http 页面：两种都能用
  return /^https:\/\//i.test(url);          // https 页面：只能用 https
}

/** 站点的候选播放地址：优先 https，同址重试失败后回落 http
 *
 *  httpsOk 是「该流是否支持 https」的实测结论（见 stations.js）：
 *    true  → https 直接可用，只给 https
 *    false → 已知纯 HTTP，跳过 https 尝试（否则每次白等 3 轮重试 ≈ 3.6 秒）
 *    null  → 未知（用户自建电台），先试 https，失败再回落
 */
function candidatesFor(urls, httpsOk) {
  const out = [];
  for (const raw of urls) {
    const u = String(raw || '').trim();
    if (!u) continue;
    const https = httpsOf(u);
    const http  = /^https:\/\//i.test(u) ? u.replace(/^https:\/\//i, 'http://') : u;

    if (httpsOk === true) { out.push(https); continue; }
    if (httpsOk === false) { out.push(http); continue; }

    out.push(https);
    if (http !== https) out.push(http);
  }
  return out;
}

const isHttpsUrl = (u) => /^https:\/\//i.test(u);

// ================= 播放核心 =================

const audio = new Audio();
audio.preload = 'none';
audio.crossOrigin = null;      // 不设，避免给纯音频流强加 CORS 要求

let state = STATE.IDLE;
let cur = { name: '', urls: [], index: 0, retry: 0, lastError: '' };
let pendingTimer = null;
let stallTimer = null;
let icyTimer = null;
let lastIcy = '';
let wakeLock = null;

function setState(s) {
  state = s;
  refreshUI();
}

function playStation(name, urls, opts) {
  opts = opts || {};
  cur = { name: name, urls: candidatesFor(urls, opts.httpsOk), index: 0, retry: 0, lastError: '' };

  store.lastStationUrl  = urls[0] || '';
  store.lastName        = name;
  save();

  el.stationName.textContent = name;
  el.stationMeta.textContent = metaOf(name) || '';
  lastIcy = '';
  startCurrent(false);
}

function startCurrent(keepAudio) {
  clearTimeout(pendingTimer);
  clearTimeout(stallTimer);
  if (!cur.urls.length) return;

  const url = cur.urls[cur.index];
  cur.lastError = '';

  // https 页面 + http 地址 = 必被浏览器拦。这个失败是确定的，重试多少次都一样，
  // 所以直接跳到下一个候选地址，不浪费时间在无谓的重试上。
  if (!usable(url)) {
    cur.lastError = '页面为 HTTPS，无法加载 HTTP 流源（混合内容拦截）';
    if (cur.index + 1 < cur.urls.length) {
      cur.index++;
      pendingTimer = setTimeout(() => startCurrent(false), 0);
      return;
    }
    cur.index = 0;
    setState(STATE.ERROR);
    toast(`${cur.name} 播放失败：源是 HTTP 流，在 HTTPS 页面上被浏览器拦截。`
        + `本地 http:// 打开，或打包成 APK 后即可播放。`, 6000);
    return;
  }

  setState(STATE.PREPARING);
  stopIcyPolling();

  try {
    audio.pause();
    audio.removeAttribute('src');
    audio.load();                 // 先断开上一条流，避免连接泄漏
    audio.src = url;
    audio.load();
    if (!keepAudio) audio.volume = 1;
    // 起了缓冲看门狗：超时仍没进入 PLAYING 就当作失败，走重试/换源。
    // 没有它，喂不满缓冲的源会让界面永远停在「缓冲中」。
    stallTimer = setTimeout(() => {
      if (state === STATE.PREPARING) onFailed('缓冲超时，源没有数据');
    }, BUFFER_STALL_MS);
    const p = audio.play();
    if (p && p.catch) p.catch((e) => {
      // NotAllowedError = 浏览器拦截自动播放；其余交给 error 事件
      if (e && e.name === 'NotAllowedError') {
        setState(STATE.IDLE);
        toast('浏览器拦截了自动播放，请再点一次播放键');
      }
    });
  } catch (e) {
    onFailed('地址无效: ' + (e && e.name ? e.name : 'Error'));
  }
}

function onFailed(reason) {
  cur.lastError = reason || '播放失败';
  cur.retry++;
  console.warn('[RadioFM] 失败', cur.retry, cur.lastError, cur.urls[cur.index]);

  if (cur.retry <= MAX_RETRY_PER_URL) {
    pendingTimer = setTimeout(() => startCurrent(true), RETRY_DELAY_MS);
    return;
  }
  cur.retry = 0;
  if (cur.index + 1 < cur.urls.length) {
    cur.index++;
    pendingTimer = setTimeout(() => startCurrent(false), SWITCH_DELAY_MS);
    return;
  }
  // 所有地址都失败
  cur.index = 0;
  setState(STATE.ERROR);
  toast(`${cur.name} 播放失败：${cur.lastError}`, 4000);
}

function describeMediaError() {
  const e = audio.error;
  if (!e) return '播放失败';
  switch (e.code) {
    case 1: return '播放被中止';
    case 2: return '网络错误（源不可达或被拦截）';
    case 3: return '解码失败（格式不支持）';
    case 4: return '流地址已失效或格式不支持';
    default: return '播放失败(' + e.code + ')';
  }
}

function pause() {
  if (state === STATE.PLAYING || state === STATE.PREPARING) {
    clearTimeout(pendingTimer);
    clearTimeout(stallTimer);   // 手动暂停不算缓冲失败
    audio.pause();
    stopIcyPolling();
    releaseWakeLock();
    setState(STATE.IDLE);
  }
}

function resume() {
  if (!cur.urls.length) return;
  if (audio.src && state === STATE.IDLE && !audio.ended) {
    audio.play().then(() => setState(STATE.PREPARING)).catch(() => startCurrent(true));
  } else {
    startCurrent(false);
  }
}

function toggle() {
  if (state === STATE.PLAYING || state === STATE.PREPARING) pause();
  else resume();
}

function stop() {
  clearTimeout(pendingTimer);
  clearTimeout(stallTimer);      // 用户主动停，别再触发"缓冲超时"换源
  stopIcyPolling();
  releaseWakeLock();
  try { audio.pause(); audio.removeAttribute('src'); audio.load(); } catch (e) {}
  cur = { name: '', urls: [], index: 0, retry: 0, lastError: '' };
  lastIcy = '';
  el.icyText.hidden = true;
  el.stationName.textContent = '网络收音机';
  el.stationMeta.textContent = '';
  setState(STATE.IDLE);
}

// audio 事件
audio.addEventListener('playing', () => {
  cur.retry = 0;
  clearTimeout(stallTimer);      // 出声了，撤掉缓冲看门狗
  setState(STATE.PLAYING);
  acquireWakeLock();
  startIcyPolling();
});
audio.addEventListener('waiting', () => { if (state === STATE.PLAYING) setState(STATE.PREPARING); });
audio.addEventListener('error', () => {
  if (!audio.src) return;
  onFailed(describeMediaError());
});
audio.addEventListener('stalled', () => { if (state === STATE.PLAYING) setState(STATE.PREPARING); });

// ================= 屏幕常亮 =================

async function acquireWakeLock() {
  try {
    if ('wakeLock' in navigator && !wakeLock) {
      wakeLock = await navigator.wakeLock.request('screen');
      wakeLock.addEventListener('release', () => { wakeLock = null; });
    }
  } catch (e) { /* 不支持就算了 */ }
}
function releaseWakeLock() {
  try { if (wakeLock) { wakeLock.release(); wakeLock = null; } } catch (e) {}
}

// ================= ICY 曲目信息 =================

function stopIcyPolling() { clearInterval(icyTimer); icyTimer = null; }

function startIcyPolling() {
  stopIcyPolling();
  if (!store.showIcy) return;
  const tick = () => {
    if (state !== STATE.PLAYING) return;
    const url = cur.urls[cur.index];
    if (!url) return;
    fetchIcyTitle(url).then((title) => {
      if (!title || title === lastIcy) return;
      lastIcy = title;
      el.icyText.textContent = '♪ ' + title;
      el.icyText.hidden = false;
    }).catch(() => {});
  };
  tick();
  icyTimer = setInterval(tick, ICY_INTERVAL_MS);
}

/** 取 ICY 元数据：icy-metaint 字节音频后跟长度/16 的元数据块，块里是 StreamTitle='...' */
async function fetchIcyTitle(url) {
  // https 页面下取 http 流会被拦，先升级成 https 再试
  const target = SECURE ? httpsOf(url) : url;
  const reqUrl = ICY_PROXY_PARAM ? ICY_PROXY_PARAM + encodeURIComponent(target) : target;

  const ac = new AbortController();
  const to = setTimeout(() => ac.abort(), ICY_TIMEOUT_MS);
  try {
    const res = await fetch(reqUrl, {
      signal: ac.signal,
      headers: { 'Icy-MetaData': '1', 'User-Agent': 'WinampMPEG/5.09' },
    });
    if (!res.ok) return null;

    const metaInt = parseInt(res.headers.get('icy-metaint') || '0', 10);
    if (!metaInt || metaInt <= 0) return null;         // 该流不带元数据

    const reader = res.body.getReader();
    let skipped = 0, scanned = 0;
    const MAX_SCAN = 96 * 1024;

    while (scanned < MAX_SCAN) {
      // 跳过 metaInt 字节音频
      while (skipped < metaInt) {
        const { value, done } = await reader.read();
        if (done) { reader.cancel(); return null; }
        skipped += value.length;
        scanned += value.length;
        if (scanned >= MAX_SCAN) { reader.cancel(); return null; }
      }
      const head = await reader.read();
      if (head.done) { reader.cancel(); return null; }
      const lenByte = head.value[0];
      skipped = 0;
      if (lenByte === 0) continue;                     // 本次无标题
      const metaLen = lenByte * 16;
      if (metaLen > 4080) { reader.cancel(); return null; }

      const meta = new Uint8Array(metaLen);
      let got = 0;
      while (got < metaLen) {
        const { value, done } = await reader.read();
        if (done) break;
        const take = Math.min(value.length, metaLen - got);
        meta.set(value.subarray(0, take), got);
        got += take;
        scanned += take;
      }
      const title = parseStreamTitle(new TextDecoder('utf-8').decode(meta.subarray(0, got)));
      reader.cancel();
      if (title) return title;
    }
    reader.cancel();
    return null;
  } finally {
    clearTimeout(to);
  }
}

function parseStreamTitle(meta) {
  const i = meta.indexOf("StreamTitle='");
  if (i < 0) return null;
  const start = i + "StreamTitle='".length;
  let end = meta.indexOf("';", start);
  if (end < 0) end = meta.indexOf("'", start);
  if (end <= start) return null;
  const t = meta.slice(start, end).trim();
  return t || null;
}

// ================= 电台数据 =================

let allStations = [];     // {name, genre, region, urls, favorite, custom}
let shown = [];

function loadStations() {
  allStations = BUILT_IN.map((s) => ({
    name: s.name, genre: s.genre, region: s.region, urls: s.urls.slice(),
    httpsOk: s.httpsOk === undefined ? null : s.httpsOk,
    custom: false,
  }));

  const favs = new Set(store.favorites || []);
  allStations.forEach((s) => { s.favorite = favs.has(s.name); });

  (store.customStations || []).forEach((c) => {
    if (!c || !c.name || !c.url) return;
    allStations.push({
      name: c.name, genre: '自定义', region: '我的',
      urls: String(c.url).split('|').filter(Boolean),
      httpsOk: c.httpsOk === undefined ? null : c.httpsOk,
      favorite: true, custom: true,
    });
  });
}

function metaOf(name) {
  const s = allStations.find((x) => x.name === name);
  return s ? subtitle(s) : '';
}

function subtitle(s) {
  const parts = [];
  if (s.region) parts.push(s.region);
  parts.push(s.genre);

  // 能走 https 就标 HTTPS；已知纯 HTTP 的标 HTTP，在 https 页面下再加个警告
  const httpsCapable = s.httpsOk !== false;
  parts.push(httpsCapable ? 'HTTPS' : 'HTTP');
  if (SECURE && !httpsCapable) parts.push('⚠ 仅 HTTP');
  return parts.join(' · ');
}

function saveFavorites() {
  store.favorites = allStations.filter((s) => s.favorite && !s.custom).map((s) => s.name);
  save();
}

function toggleFavoriteOfCurrent() {
  if (!cur.name) { toast('先选一个电台'); return; }
  const s = allStations.find((x) => x.name === cur.name);
  if (!s) { toast('这个电台不在列表里'); return; }
  s.favorite = !s.favorite;
  saveFavorites();
  refreshUI();          // 必须走 refreshUI —— 收藏按钮的文案在那里刷新，光调 render() 不会更新按钮
  toast(s.favorite ? '已加入收藏' : '已取消收藏');
}

// ================= 列表渲染 =================

function applyFilter() {
  const q = el.filterBox.value.trim().toLowerCase();
  const favOnly = el.filterMode.value === '1';
  shown = allStations.filter((s) => {
    if (favOnly && !s.favorite) return false;
    if (!q) return true;
    return s.name.toLowerCase().includes(q)
        || s.genre.toLowerCase().includes(q)
        || s.region.toLowerCase().includes(q);
  });
  render();
}

function render() {
  // 重建列表会把当前获得焦点的 <li> 销毁掉，焦点于是掉回 body ——
  // 遥控器用户按一下 ↓ 就被甩到「播放」按钮上，得重新一路按回列表。
  // 所以先记下焦点在哪条电台，重建后按名字还原。
  const act = document.activeElement;
  const keptFocus = (act && el.list.contains(act))
    ? (act.querySelector('.nm') || {}).textContent : null;

  el.list.innerHTML = '';

  if (!shown.length) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = el.filterMode.value === '1'
      ? '还没有收藏的电台 —— 播放一个后点「收藏」'
      : '没有匹配的电台，换个关键词试试';
    el.list.appendChild(li);
  }

  const curIdx = shown.findIndex((s) => s.name === cur.name);
  shown.forEach((s, i) => {
    const li = document.createElement('li');
    const isCur = s.name === cur.name;
    if (isCur) li.className = 'playing';
    li.tabIndex = 0;   // 遥控器要用方向键走到这里，必须可聚焦

    const info = document.createElement('div');
    info.className = 'info';
    const nm = document.createElement('div');
    nm.className = 'nm';
    nm.textContent = s.name;
    const sb = document.createElement('div');
    sb.className = 'sb';
    sb.textContent = subtitle(s);
    info.append(nm, sb);

    const now = document.createElement('div');
    now.className = 'now';
    if (isCur) {
      now.textContent = state === STATE.PLAYING ? '▶'
                      : state === STATE.PREPARING ? '…'
                      : state === STATE.ERROR ? '!' : '■';
    }

    const star = document.createElement('div');
    star.className = 'star';
    star.textContent = s.favorite ? '★' : '';

    const idx = allStations.indexOf(s);
    const total = Math.max(allStations.length - 1, 1);

    li.append(info, now, star);
    li.addEventListener('click', () => playStation(s.name, s.urls, { httpsOk: s.httpsOk }));
    li.addEventListener('dblclick', (e) => {
      e.preventDefault();
      s.favorite = !s.favorite;
      saveFavorites(); refreshUI();
      toast(s.favorite ? '已加入收藏' : '已取消收藏');
    });
    el.list.appendChild(li);

    if (isCur) positionNeedle(idx / total);
  });

  // 焦点还原：见函数开头。只在原本焦点确实在列表里时才动，
  // 免得不小心把焦点从输入框/按钮上抢走。
  if (keptFocus) {
    const same = Array.prototype.find.call(
      el.list.querySelectorAll('li'),
      (li) => { const n = li.querySelector('.nm'); return n && n.textContent === keptFocus; });
    if (same) same.focus();
  }

  el.countHint.textContent = '共 ' + shown.length + ' 个电台'
    + (el.filterMode.value === '1' ? ' · 收藏' : '');
}

function positionNeedle(ratio) {
  el.needle.style.left = (10 + ratio * 80).toFixed(1) + '%';
}

// ================= 界面刷新 =================

function refreshUI() {
  const text = { 0: '已停止', 1: '缓冲中…', 2: '正在播放', 3: '错误：' + (cur.lastError || '播放失败') };
  el.statusText.textContent = text[state] || '已停止';

  el.stateBadge.textContent = STATE_BADGE[state];
  el.stateBadge.className = 'badge' + (state === STATE.PLAYING ? ' live'
                        : state === STATE.PREPARING ? ' buf'
                        : state === STATE.ERROR ? ' err' : '');

  el.btnPlay.textContent = (state === STATE.PLAYING || state === STATE.PREPARING) ? '暂停' : '播放';
  el.btnPlay.classList.toggle('on', state === STATE.PLAYING || state === STATE.PREPARING);

  el.needle.classList.toggle('live', state === STATE.PLAYING);
  el.needle.classList.toggle('hold', state === STATE.PREPARING);

  if (cur.name) {
    el.stationName.textContent = cur.name;
    el.stationMeta.textContent = metaOf(cur.name) || '';
  }

  const favBtn = el.btnFav;
  const curStation = allStations.find((x) => x.name === cur.name);
  favBtn.textContent = curStation && curStation.favorite ? '★ 已收藏' : '收藏';

  if (state !== STATE.PLAYING || !store.showIcy) {
    if (state !== STATE.PLAYING) el.icyText.hidden = true;
  }

  // 列表里的播放标记要跟着状态变
  render();
}

// ================= 自定义电台 =================

function addCustomStation() {
  const name = el.addName.value.trim();
  const url = el.addUrl.value.trim();
  if (!name || !url) { toast('名称和地址都要填'); return; }
  if (!/^https?:\/\//i.test(url)) { toast('地址要以 http:// 或 https:// 开头', 3000); return; }

  store.customStations = (store.customStations || [])
    .concat([{ name: name, url: url, httpsOk: isHttpsUrl(url) ? true : null }]);
  save();
  loadStations(); applyFilter();
  closeModal(el.mAdd);
  el.addName.value = ''; el.addUrl.value = '';
  toast('已添加');
}

/** 解析 M3U / PLS → [{name, url}] */
function parsePlaylist(content) {
  const out = [];
  let pendingName = null;
  for (const raw of String(content).split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith('#')) {
      if (/^#EXTINF:/i.test(line)) {
        const comma = line.indexOf(',');
        if (comma >= 0 && comma + 1 < line.length) pendingName = line.slice(comma + 1).trim();
      }
      continue;
    }
    if (/^File\d*\s*=/i.test(line)) {
      const val = line.slice(line.indexOf('=') + 1).trim();
      const num = (line.match(/\d+/) || [])[0];
      if (val) out.push({ name: '频道 ' + (num || out.length + 1), url: val });
      continue;
    }
    if (/^Title\d*\s*=/i.test(line)) continue;
    if (/^(https?|mms):\/\//i.test(line)) {
      out.push({ name: pendingName || ('电台 ' + (out.length + 1)), url: line });
      pendingName = null;
    }
  }
  return out;
}

async function doImport() {
  const text = el.importText.value.trim();
  if (!text) return;

  let entries;
  if (/^https?:\/\//i.test(text)) {
    el.importDo.disabled = true;
    el.importDo.textContent = '抓取中…';
    try {
      const res = await fetch(text, { redirect: 'follow' });
      entries = parsePlaylist(await res.text());
    } catch (e) {
      toast('抓取播放列表失败（可能是跨域限制）', 3500);
      entries = [];
    }
    el.importDo.disabled = false;
    el.importDo.textContent = '导入';
  } else {
    entries = parsePlaylist(text);
  }

  if (!entries.length) { toast('没解析出电台'); return; }

  const existing = new Set((store.customStations || []).map((c) => c.name + '\t' + c.url));
  const fresh = entries.filter((e) => !existing.has(e.name + '\t' + e.url));
  store.customStations = (store.customStations || []).concat(fresh);
  save();
  loadStations(); applyFilter();
  closeModal(el.mImport);
  el.importText.value = '';
  toast('导入 ' + fresh.length + ' 个电台' + (entries.length - fresh.length ? '（跳过重复 ' + (entries.length - fresh.length) + '）' : ''));
}

// ================= 在线电台库 =================

const MIRRORS = [
  'https://de1.api.radio-browser.info',
  'https://nl1.api.radio-browser.info',
  'https://at1.api.radio-browser.info',
];

async function onlineSearch() {
  const q = el.onlineSearch.value.trim();
  const country = el.onlineCountry.value;
  el.onlineHint.textContent = '查询中…';
  el.onlineList.innerHTML = '';

  let params = '/json/stations/search?hidebroken=true&limit=100&order=votes&reverse=true';
  if (country) params += '&countrycode=' + encodeURIComponent(country);
  if (q) params += '&name=' + encodeURIComponent(q);

  for (const mirror of MIRRORS) {
    try {
      const res = await fetch(mirror + params, { headers: { 'User-Agent': 'RadioFM-Web/1.0' } });
      if (!res.ok) continue;
      const arr = await res.json();
      const list = arr.filter((o) => {
        const url = o.url_resolved || o.url || '';
        return o.name && url && !o.name.startsWith('http') && Number(o.hls) !== 1;
      }).map((o) => ({
        name: String(o.name).trim(),
        url: o.url_resolved || o.url,
        codec: o.codec || '?',
        bitrate: Number(o.bitrate) > 0 ? o.bitrate + 'kbps' : '未知码率',
      }));
      renderOnline(list);
      el.onlineHint.textContent = list.length
        ? '共 ' + list.length + ' 个（已过滤 HLS 流）'
        : '没有结果（换个关键词，或该地区源不支持）';
      return;
    } catch (e) { /* 换下一个镜像 */ }
  }
  el.onlineHint.textContent = '所有镜像都连不上，稍后再试';
}

function renderOnline(list) {
  el.onlineList.innerHTML = '';
  if (!list.length) return;
  for (const s of list) {
    const li = document.createElement('li');
    const info = document.createElement('div');
    info.className = 'info';
    const nm = document.createElement('div'); nm.className = 'nm'; nm.textContent = s.name;
    const sb = document.createElement('div'); sb.className = 'sb';
    sb.textContent = s.codec + ' · ' + s.bitrate + (SECURE && !isHttpsUrl(s.url) ? ' · ⚠ 仅 HTTP' : '');
    info.append(nm, sb);
    li.tabIndex = 0;   // 遥控器可达

    const add = document.createElement('button');
    add.className = 'small'; add.textContent = '＋';
    add.title = '加入我的电台';
    add.addEventListener('click', (e) => {
      e.stopPropagation();
      const dup = (store.customStations || []).some((c) => c.url === s.url);
      if (dup) { toast('已经在我的电台里了'); return; }
      store.customStations = (store.customStations || [])
        .concat([{ name: s.name, url: s.url, httpsOk: isHttpsUrl(s.url) }]);
      save(); loadStations(); applyFilter();
      toast('已加入我的电台');
    });

    li.append(info, add);
    li.addEventListener('click', () => {
      playStation(s.name, [s.url], { httpsOk: null });
      closeModal(el.mOnline);
      toast('正在播放 ' + s.name + '（源失效属正常，国内很多台有防盗链）', 3000);
    });
    el.onlineList.appendChild(li);
  }
}

// ================= 设置 =================

function setSleep(minutes) {
  store.sleepMinutes = minutes;
  store.sleepEndsAt = minutes > 0 ? Date.now() + minutes * 60000 : 0;
  save();
  updateSleepStatus();
  toast(minutes === 0 ? '已取消定时关闭' : minutes + ' 分钟后自动停止');
}

function updateSleepStatus() {
  const m = store.sleepMinutes;
  if (!m) { el.sleepStatus.textContent = ''; return; }
  const left = Math.max(0, Math.round((store.sleepEndsAt - Date.now()) / 1000));
  const mm = Math.floor(left / 60), ss = left % 60;
  el.sleepStatus.textContent = '已设置：' + m + ' 分钟后停止（还剩 '
    + mm + ':' + String(ss).padStart(2, '0') + '）';
}

setInterval(() => {
  if (!store.sleepMinutes) return;
  updateSleepStatus();
  if (store.sleepEndsAt && Date.now() >= store.sleepEndsAt) {
    stop();
    store.sleepMinutes = 0; store.sleepEndsAt = 0; save();
    updateSleepStatus();
    toast('定时关闭：已自动停止播放', 3000);
  }
}, SLEEP_TICK_MS);

// 定时唤醒：每分钟检查一次，命中就播
let lastAlarmFired = '';
setInterval(() => {
  if (!store.alarmEnabled || !store.alarmUrl) return;
  const now = new Date();
  const stamp = now.toDateString() + ' ' + now.getHours() + ':' + now.getMinutes();
  if (now.getHours() !== store.alarmHour || now.getMinutes() !== store.alarmMinute) return;
  if (lastAlarmFired === stamp) return;
  lastAlarmFired = stamp;
  const alarmSt = allStations.find((x) => x.name === store.alarmStation);
  playStation(store.alarmStation || '定时唤醒', [store.alarmUrl], { httpsOk: alarmSt ? alarmSt.httpsOk : null });
  toast('定时唤醒：' + (store.alarmStation || '') + ' 已开始播放', 3000);
}, 30000);

function openSettings() {
  el.alarmEnabled.checked = !!store.alarmEnabled;
  el.alarmHour.value = String(store.alarmHour).padStart(2, '0');
  el.alarmMinute.value = String(store.alarmMinute).padStart(2, '0');
  el.showIcy.checked = !!store.showIcy;
  el.bootResume.checked = !!store.bootResume;
  updateSleepStatus();
  updateAlarmLabel();
  openModal(el.mSettings);
}

function updateAlarmLabel() {
  el.alarmStationLabel.textContent = store.alarmStation
    ? '唤醒电台：' + store.alarmStation : '唤醒电台：未选择';
  el.alarmPick.textContent = store.alarmStation ? '更换唤醒电台' : '选择唤醒电台';
}

function saveSettings() {
  const h = clampInt(el.alarmHour.value, 0, 23, store.alarmHour);
  const m = clampInt(el.alarmMinute.value, 0, 59, store.alarmMinute);
  el.alarmHour.value = String(h).padStart(2, '0');
  el.alarmMinute.value = String(m).padStart(2, '0');

  if (el.alarmEnabled.checked && !store.alarmUrl) {
    toast('还没选唤醒电台，先点下面的按钮选一个', 3500);
    el.alarmEnabled.checked = false;
    return;
  }
  store.alarmEnabled = el.alarmEnabled.checked;
  store.alarmHour = h;
  store.alarmMinute = m;
  store.showIcy = el.showIcy.checked;
  store.bootResume = el.bootResume.checked;
  save();

  if (!store.showIcy) el.icyText.hidden = true;
  else if (lastIcy && state === STATE.PLAYING) { el.icyText.textContent = '♪ ' + lastIcy; el.icyText.hidden = false; }

  closeModal(el.mSettings);
  toast(store.alarmEnabled
    ? '设置已保存 · 每天 ' + String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ' 自动播放'
    : '设置已保存');
}

function clampInt(v, min, max, def) {
  const n = parseInt(v, 10);
  if (isNaN(n)) return def;
  return n < min ? min : (n > max ? max : n);
}

function openPickStation() {
  el.pickList.innerHTML = '';
  for (const s of allStations) {
    const li = document.createElement('li');
    const info = document.createElement('div');
    info.className = 'info';
    const nm = document.createElement('div'); nm.className = 'nm'; nm.textContent = s.name;
    const sb = document.createElement('div'); sb.className = 'sb'; sb.textContent = subtitle(s);
    info.append(nm, sb);
    li.tabIndex = 0;   // 遥控器可达
    li.appendChild(info);
    li.addEventListener('click', () => {
      store.alarmStation = s.name;
      store.alarmUrl = s.urls[0];
      save();
      updateAlarmLabel();
      closeModal(el.mPick);
      toast('唤醒电台：' + s.name);
    });
    el.pickList.appendChild(li);
  }
  openModal(el.mPick);
}

// ================= 弹层 =================

function openModal(m) { m.hidden = false; }
function closeModal(m) { m.hidden = true; }

document.addEventListener('click', (e) => {
  const t = e.target;
  if (t.hasAttribute && t.hasAttribute('data-close')) {
    const m = t.closest('.modal');
    if (m) closeModal(m);
  }
  if (t.classList && t.classList.contains('modal')) closeModal(t);   // 点遮罩关闭
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') document.querySelectorAll('.modal:not([hidden])').forEach(closeModal);
});

// ================= 事件绑定 =================

el.btnPlay.addEventListener('click', () => {
  if (state === STATE.IDLE && !cur.urls.length) {
    const s = shown[0] || allStations[0];
    if (s) playStation(s.name, s.urls, { httpsOk: s.httpsOk });
    return;
  }
  toggle();
});
el.btnStop.addEventListener('click', stop);
el.btnFav.addEventListener('click', toggleFavoriteOfCurrent);

el.filterBox.addEventListener('input', applyFilter);
el.filterMode.addEventListener('change', applyFilter);

$('btnAdd').addEventListener('click', () => openModal(el.mAdd));
el.addSave.addEventListener('click', addCustomStation);
$('btnImport').addEventListener('click', () => openModal(el.mImport));
el.importDo.addEventListener('click', doImport);
$('btnOnline').addEventListener('click', () => { openModal(el.mOnline); if (!el.onlineList.children.length) onlineSearch(); });
$('btnSettings').addEventListener('click', openSettings);
$('btnMore').addEventListener('click', () => {
  const m = document.createElement('div');
  toast('双击列表项可直接收藏/取消收藏', 3000);
  openModal(el.mSettings);
});

el.onlineGo.addEventListener('click', onlineSearch);
el.onlineSearch.addEventListener('keydown', (e) => { if (e.key === 'Enter') onlineSearch(); });
el.onlineCountry.addEventListener('change', onlineSearch);

el.alarmPick.addEventListener('click', openPickStation);
el.settingsSave.addEventListener('click', saveSettings);

document.querySelectorAll('[data-sleep]').forEach((b) => {
  b.addEventListener('click', () => setSleep(parseInt(b.dataset.sleep, 10)));
});

// 键盘：空格播放/暂停，左右切台
document.addEventListener('keydown', (e) => {
  const tag = (e.target.tagName || '').toLowerCase();
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return;
  if (document.querySelector('.modal:not([hidden])')) return;

  if (e.code === 'Space') { e.preventDefault(); el.btnPlay.click(); }
  if (e.code === 'ArrowRight' || e.code === 'ArrowLeft') {
    if (allStations.length < 2) return;
    const i = allStations.findIndex((s) => s.name === cur.name);
    const base = i < 0 ? 0 : i;
    const next = e.code === 'ArrowRight'
      ? (base + 1) % allStations.length
      : (base - 1 + allStations.length) % allStations.length;
    playStation(allStations[next].name, allStations[next].urls, { httpsOk: allStations[next].httpsOk });
  }
});

// ================= 启动 =================

function buildTicks() {
  for (let i = 0; i < 21; i++) {
    const t = document.createElement('i');
    if (i % 2 === 0) t.className = 'major';
    el.ticks.appendChild(t);
  }
}

function boot() {
  buildTicks();
  loadStations();
  applyFilter();
  refreshUI();

  if (SECURE && BUILT_IN.some((s) => s.urls.every((u) => !isHttpsUrl(u)))) {
    console.info('[RadioFM] 当前为 HTTPS 页面，纯 HTTP 的电台无法播放（浏览器混合内容拦截）。' +
                 '本地 http://localhost 打开则不受限。');
  }

  if (store.bootResume && store.lastStationUrl) {
    const s = allStations.find((x) => x.name === store.lastName);
    el.stationName.textContent = store.lastName || '网络收音机';
    el.stationMeta.textContent = s ? subtitle(s) : '';
    toast('上次收听：' + (store.lastName || '') + ' —— 点播放继续', 3200);
  }
}

boot();

/* ================= 测试钩子 =================
 *
 * 为什么需要：app.js 整体是 IIFE，audio / state / cur 全是私有的。CDP 自动化
 * （headless Chrome）从外部只能看到 DOM，而"到底有没有在出声"取决于 audio 元素
 * 的真实状态（currentTime 有没有在走、currentSrc 解析成了什么、是否被混合内容
 * 拦掉）—— 这些 DOM 上看不出来。之前的测试就因此误判过（把设置面板里的 <li>
 * 当成电台条目点了，还以为播放成功）。
 *
 * 只读、不可变、无副作用：暴露的全是取值，没有任何函数或写入口。
 * 打包成 APK 时若要收紧，删掉这个块即可，不影响任何功能。
 */
Object.defineProperty(window, '__radiofm', {
  value: Object.freeze({
    get state()      { return state; },
    get stateName()  { return STATE_BADGE[state]; },
    get currentTime(){ return audio.currentTime; },
    get paused()     { return audio.paused; },
    get src()        { return audio.currentSrc || audio.src || ''; },
    get resolved()   { return cur.urls[cur.index] || ''; },
    get station()    { return cur.name; },
    get lastError()  { return cur.lastError; },
    get secure()     { return SECURE; },
    get stations()   { return BUILT_IN; },
    get favorites()  { return store.favorites.slice(); },
  }),
  writable: false, configurable: false,
});

})();
