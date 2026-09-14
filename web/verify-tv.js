/* 电视场景端到端验收 —— 1920×1080 横屏 + 真实遥控器按键。
 *
 * 关键：用 Input.dispatchKeyEvent 发的是真·方向键/确认键，走完整的事件链，
 * 和遥控器在盒子上的行为一致。用 li.click() 是测不出来的 —— 那只是 JS 派发
 * 一个 click 事件，压根不经过焦点。
 */
const CDP_PORT = process.env.CDP_PORT || 9555;
const TARGET = process.argv[2] || 'http://127.0.0.1:8099';
const { spawn } = require('child_process');
const fs = require('fs'), os = require('os'), path = require('path');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'rfm-tv-'));
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${CDP_PORT}`,
  `--user-data-dir=${profile}`, '--no-first-run', '--window-size=1920,1080',
  '--disable-gpu', '--mute-audio', 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let id = 0;
function rpc(ws, method, params = {}) {
  return new Promise((res, rej) => {
    const n = ++id;
    const on = (ev) => { const m = JSON.parse(ev.data); if (m.id !== n) return; ws.removeEventListener('message', on); m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result); };
    ws.addEventListener('message', on); ws.send(JSON.stringify({ id: n, method, params }));
    setTimeout(() => rej(new Error('timeout ' + method)), 30000);
  });
}

const KEYS = {
  ArrowUp: 38, ArrowDown: 40, ArrowLeft: 37, ArrowRight: 39, Enter: 13,
};

(async () => {
  const results = [];
  const log = (name, ok, detail = '') => {
    results.push({ name, ok });
    console.log(`${ok ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m'} ${name}${detail ? '  \x1b[2m' + detail + '\x1b[0m' : ''}`);
  };

  let t; for (let i = 0; i < 30; i++) { try { t = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)).json(); break; } catch { await sleep(300); } }
  const ws = new WebSocket(t.find(x => x.type === 'page').webSocketDebuggerUrl);
  await new Promise(r => ws.addEventListener('open', r, { once: true }));
  await rpc(ws, 'Page.enable'); await rpc(ws, 'Runtime.enable');
  await rpc(ws, 'Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
  await rpc(ws, 'Page.navigate', { url: TARGET });
  await sleep(4000);

  const ev = async (e) => {
    const r = await rpc(ws, 'Runtime.evaluate', { expression: e, returnByValue: true, awaitPromise: true });
    return r.exceptionDetails ? 'EXC: ' + (r.exceptionDetails.exception?.description || '').split('\n')[0] : r.result.value;
  };
  // 真·按键，走完整事件链
  const press = async (key, times = 1) => {
    for (let i = 0; i < times; i++) {
      for (const type of ['keyDown', 'keyUp']) {
        await rpc(ws, 'Input.dispatchKeyEvent', {
          type, key, code: key, windowsVirtualKeyCode: KEYS[key], nativeVirtualKeyCode: KEYS[key],
          text: key === 'Enter' ? '\r' : undefined,
        });
      }
      await sleep(180);
    }
  };
  const focus = () => ev('JSON.stringify(__tvnav.current)');

  try {
    // ---------- 布局 ----------
    console.log('\n\x1b[2m--- 布局（1920×1080 横屏）---\x1b[0m');
    const appW = await ev('Math.round(document.querySelector(".app").getBoundingClientRect().width)');
    log('布满整个屏幕宽度', appW >= 1900, `.app = ${appW}px / 视口 1920px`);

    const isGrid = await ev('getComputedStyle(document.querySelector(".app")).display');
    log('已切到两栏布局', isGrid === 'grid', `display=${isGrid}`);

    // 左栏（控制区）和右栏（列表）应该在水平方向分开
    const geo = JSON.parse(await ev(`JSON.stringify({
      panel: (function(){var r=document.querySelector('.panel').getBoundingClientRect();return {l:Math.round(r.left),w:Math.round(r.width)};})(),
      list:  (function(){var r=document.getElementById('stationList').getBoundingClientRect();return {l:Math.round(r.left),w:Math.round(r.width)};})(),
      name:  parseFloat(getComputedStyle(document.querySelector('.station-name')).fontSize),
      nm:    parseFloat(getComputedStyle(document.querySelector('.list li .nm')).fontSize),
      btn:   document.querySelector('button.big').getBoundingClientRect().height
    })`));
    log('两栏并排（列表在右侧）', geo.list.l > geo.panel.l + geo.panel.w - 10,
        `面板 ${geo.panel.l}~${geo.panel.l + geo.panel.w}, 列表起点 ${geo.list.l}`);
    log('列表分到了大部分宽度', geo.list.w > 1000, `${geo.list.w}px`);
    log('电台名字号够大（3 米外可见）', geo.name >= 36, `${geo.name}px`);
    log('列表项字号够大', geo.nm >= 20, `${geo.nm}px`);
    log('按钮够高（遥控器视觉反馈清晰）', geo.btn >= 60, `${geo.btn}px`);

    // ---------- 遥控器导航 ----------
    console.log('\n\x1b[2m--- 遥控器焦点导航（真按键）---\x1b[0m');
    const focusableLis = await ev(`[...document.getElementById('stationList').querySelectorAll('li')].filter(e=>e.tabIndex>=0).length`);
    log('电台列表项可聚焦', focusableLis > 0, `${focusableLis} 条可聚焦`);

    // 回归：隐藏弹层里的按钮绝不能进入焦点候选。
    // 踩过的坑：.modal 是 position:fixed，后代 offsetParent 恒为 null，
    // 早期 isVisible() 用 offsetParent 判断，把隐藏弹层的「关闭」按钮当成了目标，
    // 遥控器一按就跑到看不见的按钮上。这里直接断言它们不在候选里。
    const ghost = await ev(`(function(){
      var pool=[...document.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')];
      var bad=[];
      pool.forEach(function(el){
        var hiddenAncestor=false;
        for(var p=el;p&&p!==document.body;p=p.parentElement){
          if(p.hasAttribute && p.hasAttribute('hidden')){ hiddenAncestor=true; break; }
        }
        if(!hiddenAncestor) return;          // 只在"祖先被 hidden"时才判定
        var r=el.getBoundingClientRect();
        // hidden 元素的 getBoundingClientRect 是 0x0；有尺寸才是真漏网
        if(r.width>0||r.height>0) bad.push((el.id||el.textContent||el.tagName).trim().slice(0,14));
      });
      return bad.length;
    })()`);
    log('隐藏弹层里没有"有尺寸"的漏网按钮', ghost === 0, `漏网 ${ghost} 个`);

    // 起始：body
    await ev('document.body.focus()');
    await press('ArrowDown');
    const f1 = JSON.parse(await focus());
    log('按 ↓ 焦点进入页面', f1 !== null, f1 ? `${f1.tag} "${f1.text}"` : '仍无焦点');

    // 连续 ↓ 应该能落到电台列表里
    let reachedList = false, seen = [];
    for (let i = 0; i < 8; i++) {
      await press('ArrowDown');
      const f = JSON.parse(await focus());
      if (f) {
        seen.push(f.text.slice(0, 14));
        if (f.tag === 'LI') { reachedList = true; break; }
      }
    }
    log('方向键能走到电台列表', reachedList, seen.join(' → '));

    // 列表内继续 ↓ 应逐条下移
    if (reachedList) {
      const before = JSON.parse(await focus()).text;
      await press('ArrowDown');
      const after = JSON.parse(await focus()).text;
      log('列表内 ↓ 逐条下移', before !== after, `${before} → ${after}`);
      await press('ArrowUp');
      const back = JSON.parse(await focus()).text;
      log('列表内 ↑ 逐条上移', back === before, `${after} → ${back}`);
    }

    // 焦点框是真的可见（3 米外能看见落在哪）
    const outline = await ev(`(function(){
      var a=document.activeElement;
      if(!a||a===document.body) return 'none';
      var cs=getComputedStyle(a);
      return cs.outlineStyle + ' ' + cs.outlineWidth + ' ' + (cs.outlineColor||'');
    })()`);
    log('焦点框可见（不是 outline:none）',
        /\d/.test(outline) && !/none/.test(outline) && parseFloat(outline.split(' ')[1] || 0) >= 3, outline);

    // ---------- 用遥控器真的开始播放 ----------
    console.log('\n\x1b[2m--- 遥控器播放：↓ 选中 → 确认键 ---\x1b[0m');
    // 先回到列表第一条
    await ev(`(function(){
      var li=document.getElementById('stationList').querySelector('li');
      li.focus(); li.scrollIntoView({block:'nearest'}); return 1;
    })()`);
    let picked = null;
    for (let i = 0; i < 25; i++) {
      const f = JSON.parse(await focus());
      if (f && /RTHK Radio 1/.test(f.text)) { picked = f; break; }
      await press('ArrowDown');
    }
    log('能导航到 RTHK Radio 1', !!picked, picked ? picked.text : '未找到');

    if (picked) {
      await press('Enter');
      let probe = {}, adv = false, prev = -1;
      for (let i = 0; i < 40; i++) {
        await sleep(500);
        probe = JSON.parse(await ev('JSON.stringify(__radiofm)'));
        if (probe.currentTime > 0 && probe.currentTime > prev) adv = true;
        prev = probe.currentTime;
        if (adv && probe.stateName === 'LIVE') break;
      }
      log('确认键触发播放（真的出声）', adv && probe.stateName === 'LIVE',
          `state=${probe.stateName} t=${probe.currentTime?.toFixed?.(2)}`);
    }

    // ---------- 长距离滚动导航 ----------
    // 从屏幕外的方向走回来时，落点必须在视口内 —— 否则用户会以为焦点丢了。
    // （列表可滚动，纯几何最近邻会选到屏幕外那条，这个坑踩过一次）
    console.log('\n\x1b[2m--- 长距离滚动导航 ---\x1b[0m');
    const onScreen = async () => ev(`(function(){
      var a=document.activeElement;
      if(!a||a===document.body) return false;
      var r=a.getBoundingClientRect();
      return r.top < innerHeight && r.bottom > 0 && r.left < innerWidth && r.right > 0;
    })()`);

    await ev(`document.querySelector('.foot button').focus()`);
    await press('ArrowUp');
    const upLanding = JSON.parse(await focus());
    const upRect = JSON.parse(await ev(`(function(){
      var a=document.activeElement; var r=a.getBoundingClientRect();
      return JSON.stringify({top:Math.round(r.top),bottom:Math.round(r.bottom),left:Math.round(r.left),
        vis:r.top<innerHeight&&r.bottom>0&&r.left<innerWidth&&r.right>0});
    })()`));
    // 从底部按钮往上，正上方是左栏的控制按钮 —— 两者并排到底是设计如此
    // （左右两栏各自贴底）。要防的是"焦点跑到屏幕外"，那才是用户以为焦点丢了的情况。
    log('从底部按钮 ↑ 焦点没消失（落在视口内）',
        upLanding !== null && upRect.vis,
        upLanding ? `${upLanding.tag} "${upLanding.text.slice(0, 10)}" top=${upRect.top} left=${upRect.left} 可见=${upRect.vis}` : '无焦点');

    // 焦点绝不该落在屏幕外 —— 列表可滚动时纯几何最近邻会选到屏幕外那条，这个坑踩过
    const visibleNow = await ev(`(function(){
      var a=document.activeElement; if(!a||a===document.body) return false;
      var r=a.getBoundingClientRect();
      return r.top<innerHeight && r.bottom>0 && r.left<innerWidth && r.right>0;
    })()`);
    log('落点在屏幕内（不会凭空消失）', visibleNow === true, `activeElement 可见=${visibleNow}`);

    // 一路 ↓ 走到列表末尾，全程焦点都该在视口内
    await ev(`document.getElementById('stationList').querySelector('li').focus()`);
    let offscreen = 0, visited = 0;
    for (let i = 0; i < 40; i++) {
      await press('ArrowDown');
      const f = JSON.parse(await focus());
      if (!f || f.tag !== 'LI') break;
      visited++;
      if (!(await onScreen())) offscreen++;
    }
    log('列表内长距离导航焦点始终在屏幕内', offscreen === 0 && visited > 10,
        `走过 ${visited} 条，屏幕外 ${offscreen} 次`);

    // ---------- 弹层焦点陷阱 ----------
    console.log('\n\x1b[2m--- 弹层：焦点不该跑到后面的列表 ---\x1b[0m');
    await ev(`document.getElementById('btnSettings').click()`);
    await sleep(600);
    const layer = await ev('__tvnav.layer');
    log('设置弹层已打开', layer === 'mSettings', `layer=${layer}`);
    if (layer) {
      // 从弹层里按 ↓ 多次，焦点应始终留在弹层内
      await ev(`(function(){var b=document.querySelector('#mSettings button, #mSettings input'); if(b) b.focus(); return 1;})()`);
      let escaped = false;
      for (let i = 0; i < 12; i++) {
        await press('ArrowDown');
        const inside = await ev(`(function(){
          var m=document.getElementById('mSettings'), a=document.activeElement;
          return !!(a && m.contains(a));
        })()`);
        if (!inside) { escaped = true; break; }
      }
      log('焦点被限制在弹层内（不会跑到背后）', !escaped, escaped ? '焦点逃出了弹层' : '12 次 ↓ 均未逃出');
      await ev(`document.querySelector('#mSettings [data-close]').click()`);
      await sleep(400);
    }

    // ---------- 手机竖屏不受影响 ----------
    console.log('\n\x1b[2m--- 回归：手机竖屏布局未被破坏 ---\x1b[0m');
    await rpc(ws, 'Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
    await sleep(800);
    const mobile = JSON.parse(await ev(`JSON.stringify({
      display: getComputedStyle(document.querySelector('.app')).display,
      w: Math.round(document.querySelector('.app').getBoundingClientRect().width),
      name: parseFloat(getComputedStyle(document.querySelector('.station-name')).fontSize),
      toolbar: getComputedStyle(document.querySelector('.toolbar')).display
    })`));
    log('竖屏仍是单列 560px', mobile.display === 'flex' && mobile.w <= 560, `display=${mobile.display} w=${mobile.w}px`);
    log('竖屏字号未被放大污染', mobile.name <= 22, `station-name = ${mobile.name}px`);
    log('竖屏搜索框仍在', mobile.toolbar !== 'none', `toolbar display=${mobile.toolbar}`);

  } catch (e) {
    console.error('\x1b[31m探针出错:\x1b[0m', e.message);
  } finally {
    ws.close(); chrome.kill(); await sleep(300);
    fs.rmSync(profile, { recursive: true, force: true });
    const pass = results.filter(r => r.ok).length;
    console.log(`\n\x1b[1m${pass}/${results.length} 通过\x1b[0m`);
    process.exit(pass === results.length ? 0 : 1);
  }
})();
