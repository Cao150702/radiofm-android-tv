/* 截图：电视横屏 + 手机竖屏，并演示焦点框 */
const { spawn } = require('child_process'); const fs = require('fs'), os = require('os'), path = require('path');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const p = fs.mkdtempSync(path.join(os.tmpdir(), 'rfm-shot-'));
const c = spawn(CHROME, ['--headless=new', '--remote-debugging-port=9999', `--user-data-dir=${p}`, '--no-first-run', '--window-size=1920,1080', '--disable-gpu', '--mute-audio', 'about:blank'], { stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms)); let id = 0;
function rpc(ws, m, pa = {}) { return new Promise((res, rej) => { const n = ++id; const on = e => { const j = JSON.parse(e.data); if (j.id !== n) return; ws.removeEventListener('message', on); j.error ? rej(new Error(JSON.stringify(j.error))) : res(j.result) }; ws.addEventListener('message', on); ws.send(JSON.stringify({ id: n, method: m, params: pa })); setTimeout(() => rej(new Error('timeout')), 20000) }) }
const KEYS = { ArrowUp: 38, ArrowDown: 40, ArrowLeft: 37, ArrowRight: 39, Enter: 13 };
(async () => {
  let t; for (let i = 0; i < 30; i++) { try { t = await (await fetch('http://127.0.0.1:9999/json/list')).json(); break } catch { await sleep(300) } }
  const ws = new WebSocket(t.find(x => x.type === 'page').webSocketDebuggerUrl);
  await new Promise(r => ws.addEventListener('open', r, { once: true }));
  await rpc(ws, 'Page.enable'); await rpc(ws, 'Runtime.enable');
  const ev = async e => { const r = await rpc(ws, 'Runtime.evaluate', { expression: e, returnByValue: true }); return r.exceptionDetails ? 'EXC' : r.result.value };
  const shot = async (f) => { const s = await rpc(ws, 'Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(f, Buffer.from(s.data, 'base64')); console.log('  ->', f); };
  const press = async (k) => { for (const type of ['keyDown', 'keyUp']) await rpc(ws, 'Input.dispatchKeyEvent', { type, key: k, code: k, windowsVirtualKeyCode: KEYS[k], nativeVirtualKeyCode: KEYS[k], text: k === 'Enter' ? '\r' : undefined }); await sleep(200) };

  // ---- 电视横屏 ----
  console.log('电视横屏 1920x1080:');
  await rpc(ws, 'Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
  await rpc(ws, 'Page.navigate', { url: 'http://127.0.0.1:8099' }); await sleep(4500);

  // 用遥控器导航到列表里的 RTHK 并播放，让画面里焦点框 + 播放态都有
  await ev("document.getElementById('stationList').querySelector('li').focus()");
  await press('Enter');
  await sleep(5000);
  await shot('/tmp/tv-1-playing.png');

  // 再按 ↓ 两次，展示焦点框在列表里移动
  await press('ArrowDown'); await press('ArrowDown');
  await shot('/tmp/tv-2-focus.png');

  // 焦点走到控制按钮
  await ev("document.getElementById('btnPlay').focus()");
  await shot('/tmp/tv-3-buttons.png');

  // ---- 手机竖屏 ----
  console.log('手机竖屏 390x844:');
  await rpc(ws, 'Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await sleep(1200);
  await shot('/tmp/tv-4-portrait.png');

  ws.close(); c.kill(); await sleep(300); fs.rmSync(p, { recursive: true, force: true });
})();
