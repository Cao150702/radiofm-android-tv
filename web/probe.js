/* 启动探针 —— 只负责一件事：在浏览器能力不足时，把原因明明白白告诉用户。
 *
 * 背景：这套 web 版最终是要塞进原生壳子（Capacitor / WebView）当 APK 用的，
 * 浏览器只是开发期预览。但 iOS Safari 对音频自动播放限制极严，纯页面形式
 * 基本播不响，所以这里主动提示，免得以为是代码坏了。
 */
(function () {
  'use strict';

  var ua = navigator.userAgent;
  var isIOS = /iPad|iPhone|iPod/.test(ua) ||
              (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  var standalone = window.navigator.standalone === true ||
                   (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);

  // 只提示一次，别每次刷新都烦人
  if (sessionStorage.getItem('radiofm_probe_dismissed') === '1') return;
  if (!isIOS) { maybeShowHttpBanner(); return; }

  showBanner(
    standalone ? 'iOS 独立窗口模式' : 'iOS 浏览器限制',
    standalone
      ? '已添加到主屏幕。若播放没反应，多半是 iOS 的音频策略所限 —— 本页面是开发预览，正式版跑在原生壳子里不受此限。'
      : 'iOS Safari 对音频自动播放限制很严，点播放可能没反应。建议：① 点底部分享 →「添加到主屏幕」再打开；② 或用安卓机测试 —— 正式版是 APK，不受此限。'
  );
  maybeShowHttpBanner();

  function maybeShowHttpBanner() {
    if (location.protocol !== 'https:') return;
    // https 页面下 http:// 的电台会被浏览器拦，提前说清楚
    setTimeout(function () {
      if (!window.__radiofmHttpBlocked) return;
    }, 0);
  }

  function showBanner(title, body) {
    var bar = document.createElement('div');
    bar.id = 'probeBanner';
    bar.style.cssText = [
      'position:fixed', 'left:8px', 'right:8px', 'top:calc(8px + env(safe-area-inset-top))',
      'z-index:200', 'background:#3E2723', 'color:#FFF8E1',
      'border-radius:10px', 'padding:12px 34px 12px 14px',
      'font-size:12.5px', 'line-height:1.6', 'box-shadow:0 6px 20px rgba(0,0,0,.3)',
    ].join(';');

    var h = document.createElement('div');
    h.textContent = '⚠ ' + title;
    h.style.cssText = 'font-weight:700;margin-bottom:4px;font-size:13px';

    var p = document.createElement('div');
    p.textContent = body;

    var x = document.createElement('button');
    x.textContent = '×';
    x.setAttribute('aria-label', '关闭');
    x.style.cssText = [
      'position:absolute', 'top:6px', 'right:8px', 'width:26px', 'height:26px',
      'border:0', 'background:transparent', 'color:#FFF8E1', 'font-size:20px',
      'line-height:1', 'cursor:pointer', 'padding:0',
    ].join(';');
    x.onclick = function () {
      bar.remove();
      sessionStorage.setItem('radiofm_probe_dismissed', '1');
    };

    bar.append(h, p, x);
    document.body.appendChild(bar);
  }

  // 全局暴露给 app.js：混合内容拦截时标记一下
  window.__radiofmMarkHttpBlocked = function () { window.__radiofmHttpBlocked = true; };
})();
