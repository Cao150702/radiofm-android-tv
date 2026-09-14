/* 遥控器（D-pad）焦点导航 —— 给电视盒子 / 智能电视用。
 *
 * 为什么必须自己写：
 *   Chrome 内置的键盘导航只有 Tab 键。方向键在浏览器里默认只滚页面，
 *   不会移动焦点 —— 实测在 1920×1080 下连按 10 次方向键，activeElement
 *   一直是 body。遥控器发出的正是方向键，所以不写这个文件，遥控器就是块砖。
 *
 * 导航算法：几何最近邻。
 *   按下方向键时，把所有可聚焦元素按"是否在目标方向上"过滤，
 *   再用「轴向距离 + 垂直偏移惩罚」排序取最优。这是空间导航的通用做法
 *   （游戏手柄 UI、TV 应用框架都是这个思路），比写死的焦点链更耐改版。
 *
 * 只在收到方向键时才激活，对触摸/鼠标设备零影响（它们不发 keydown）。
 */
(function () {
  'use strict';

  var FOCUSABLE = [
    'a[href]', 'button:not([disabled])', 'input:not([disabled])',
    'select:not([disabled])', 'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(',');

  // 在这些控件里，方向键是"编辑文本/切换选项"用的，不能抢
  function isTextEntry(el) {
    if (!el) return false;
    var tag = el.tagName;
    if (tag === 'TEXTAREA') return true;
    if (tag === 'SELECT') return true;              // 方向键切换选项
    if (tag === 'INPUT') {
      var t = (el.type || 'text').toLowerCase();
      return ['text', 'search', 'url', 'email', 'password', 'number', 'tel'].indexOf(t) >= 0;
    }
    if (el.isContentEditable) return true;
    return false;
  }

  function isVisible(el) {
    // 沿祖先链检查 hidden / display:none —— 必须显式做，不能只靠 offsetParent。
    // 坑：.modal 是 position:fixed，其后代的 offsetParent 恒为 null（就算显示着也
    // 一样），所以光看 offsetParent 会把隐藏弹层里的按钮当成可聚焦目标，
    // 遥控器就会跑到一个看不见的「关闭」按钮上。
    for (var p = el; p && p !== document.body; p = p.parentElement) {
      if (p.hasAttribute && p.hasAttribute('hidden')) return false;
      var ps = getComputedStyle(p);
      if (ps.display === 'none' || ps.visibility === 'hidden') return false;
    }
    var cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
    // 真实占位检查：零尺寸的元素接不了焦点
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  function focusables() {
    var all = Array.prototype.slice.call(document.querySelectorAll(FOCUSABLE));
    return all.filter(function (el) {
      if (el.disabled) return false;
      if (el.getAttribute('aria-hidden') === 'true') return false;
      return isVisible(el);
    });
  }

  var DIRS = {
    ArrowUp:    { axis: 'y', sign: -1 },
    ArrowDown:  { axis: 'y', sign: 1 },
    ArrowLeft:  { axis: 'x', sign: -1 },
    ArrowRight: { axis: 'x', sign: 1 },
  };

  /** 从 from 出发，朝 dir 方向找下一个焦点 */
  function findNext(from, dir) {
    var list = focusables();
    if (!list.length) return null;

    // 还没焦点时：向下/向右取第一个，向上/向左取最后一个（自然入场）
    if (!from || from === document.body) {
      return dir.sign > 0 ? list[0] : list[list.length - 1];
    }

    var fr = from.getBoundingClientRect();
    var fx = fr.left + fr.width / 2;
    var fy = fr.top + fr.height / 2;

    var best = null, bestScore = Infinity;

    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === from) continue;
      // 列表项内部的可聚焦子元素（如"＋"按钮）由它自己接焦点，不跳过

      var r = el.getBoundingClientRect();
      var ex = r.left + r.width / 2;
      var ey = r.top + r.height / 2;

      var dAxis, dPerp;
      if (dir.axis === 'y') {
        dAxis = (ey - fy) * dir.sign;      // 沿目标方向的推进量，必须 > 0
        dPerp = Math.abs(ex - fx);         // 垂直方向偏移
      } else {
        dAxis = (ex - fx) * dir.sign;
        dPerp = Math.abs(ey - fy);
      }
      if (dAxis <= 1) continue;            // 不在这个方向上

      // 垂直偏移越远惩罚越重 —— 保证"往下走"优先走正下方那一条。
      // 权重 6 是调过的：2.2 时，从底部按钮按 ↑ 会跳到"轴向最近"的那条，
      // 而它可能在屏幕外（列表可滚动时，第 8 条的中心离底部按钮最近），
      // 用户看到焦点凭空消失。加大垂直权重后，同列的列表顶部会赢 —— 符合直觉。
      var score = dAxis + dPerp * 6;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    return best;
  }

  function moveFocus(el) {
    if (!el) return false;
    el.focus();
    // 焦点进了滚动容器就把它带进视野。block:'nearest' 避免整页跳动
    try { el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) {
      el.scrollIntoView(false);
    }
    return true;
  }

  /** 焦点是否被困在弹层里 —— 弹层打开时不允许跑到后面的列表上 */
  function activeLayer() {
    var modals = document.querySelectorAll('.modal');
    for (var i = modals.length - 1; i >= 0; i--) {
      if (!modals[i].hidden) return modals[i];
    }
    return null;
  }

  document.addEventListener('keydown', function (e) {
    var key = e.key;

    // ---- 确认键：让不可聚焦的元素（列表项）也能被"点" ----
    if (key === 'Enter' || key === ' ') {
      var ae = document.activeElement;
      if (!ae || ae === document.body) return;
      // 原生 button/input 自己会响应 Enter（浏览器默认行为），
      // 但列表项是 <li> 不会 —— 这一类我们手动补上点击。
      var native = /^(BUTTON|A|INPUT|SELECT|TEXTAREA)$/.test(ae.tagName);
      if (!native) {
        e.preventDefault();
        ae.click();
      }
      return;
    }

    var dir = DIRS[key];
    if (!dir) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;

    var ae2 = document.activeElement;
    if (isTextEntry(ae2)) return;          // 输入框里方向键归输入框
    if (activeLayer() && ae2 && activeLayer().contains(ae2)) {
      // 弹层内导航仍走通用算法，但候选只取弹层里的
    }

    var next = findNext(ae2, dir);
    // 弹层打开时，不允许焦点跑到弹层外
    var layer = activeLayer();
    if (layer) {
      var inLayer = next && layer.contains(next);
      if (!inLayer) {
        var pool = focusables().filter(function (el) { return layer.contains(el); });
        if (pool.length) {
          next = findNext(ae2 && layer.contains(ae2) ? ae2 : null, dir);
          if (!next || !layer.contains(next)) {
            next = dir.sign > 0 ? pool[0] : pool[pool.length - 1];
          }
        }
      }
    }

    // 列表滚动到边缘时，方向键交还给浏览器做正常滚动
    if (!next) return;

    e.preventDefault();
    moveFocus(next);
  }, true);

  // 首次收到方向键时，主动把焦点放进页面（否则从 body 出发体验很怪）
  document.addEventListener('keydown', function (e) {
    if (!DIRS[e.key]) return;
    if (document.activeElement && document.activeElement !== document.body) return;
    var first = focusables()[0];
    if (first) first.focus();
  }, true);

  // 暴露给测试：确认导航确实生效
  Object.defineProperty(window, '__tvnav', {
    value: Object.freeze({
      get focusables() { return focusables().length; },
      get current() {
        var a = document.activeElement;
        if (!a || a === document.body) return null;
        return { tag: a.tagName, id: a.id || null, text: (a.textContent || '').trim().slice(0, 30) };
      },
      get layer() { var l = activeLayer(); return l ? l.id : null; },
    }),
    writable: false, configurable: false,
  });
})();
