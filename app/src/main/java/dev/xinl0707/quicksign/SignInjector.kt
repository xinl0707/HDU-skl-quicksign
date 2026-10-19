package dev.xinl0707.quicksign

import org.json.JSONObject

/**
 * 注入到上课啦签到系统页面里的 JavaScript 引擎。
 *
 * 结构分析结论（基于 skl.hdu.edu.cn 前端源码）：
 * - /sign/in 密令签到页：Vant 4 位密码输入 + Vant 数字键盘（按键只响应 touch 事件）+ 阿里云滑块验证 + 定位
 * - /sign/in/valid 图片验证码页：同样的 4 位输入组件，输满自动提交 /checkIn/valid-code
 * - 登录页：数字杭电 SSO 统一认证（sso.hdu.edu.cn），账号密码同数字杭电
 * - 输入 4 位码后站点自己会触发滑块验证并提交，App 只需要把数字"打"进键盘即可
 *
 * 注入可靠性要点（真机实测后修复）：
 * - Vant 密码输入框显示的是圆点（.van-password-input__item-icon），不显示数字，
 *   不能用"读取文本"做校验，否则会误判失败并重复按同一个键（2588 会变成 2222）；
 *   改为统计已填圆点数量做校验，确认按键未生效才允许重按（最多 2 次/键）。
 * - 按键只响应 touchstart/touchend，用合成 TouchEvent 模拟；先按可见键盘，找不到再全量找。
 * - 输入前先清空页面上残留的输入，避免上一轮失败留下脏数据。
 */
object SignInjector {

    const val HOME_URL = "https://skl.hdu.edu.cn/#/sign/in"

    /** 页面状态探测：返回 JSON 字符串 */
    val JS_DETECT_STATE: String = """
        (function(){
          var pw = document.querySelector('input[type=password]');
          var hasPw = !!pw;
          var captcha = false;
          if (hasPw) {
            captcha = !!document.querySelector('img[src*="captcha" i], img[src*="code" i], img[src*="verify" i], [id*="captcha" i], [class*="captcha" i]');
          }
          var sign = !!document.getElementById('captcha-trigger-btn');
          var sign2 = !!document.querySelector('.stu-sign');
          var loc = !!document.querySelector('.sign-page .tip-item.location');
          var hash = window.location.hash || '';
          var signed = hash.indexOf('/sign/in/detail') >= 0;
          return JSON.stringify({hasPw: hasPw, captcha: captcha, sign: sign, sign2: sign2, loc: loc, signed: signed, hash: hash});
        })()
    """.trimIndent()

    /**
     * 自动登录脚本：检测到密码输入框即视为登录页（统一认证 Angular/Ionic 表单）。
     * v1.5 加固：
     * - 账号框用精确选择器（name=username / id=nameInput 优先），避免选中页面其它文本框；
     * - 填值后用 input/change/keyup/ionInput 完整事件序列，确保 Angular model 同步；
     * - submit=true 时等待 800ms 让 model 同步后再点登录按钮（避免 hidden 字段为空导致"账号不行"）。
     * 返回：will-submit / filled-only / no-login-page / no-uname
     */
    fun jsAutoLogin(username: String, password: String, submit: Boolean = true): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        val submitJs = if (submit) """
              setTimeout(function(){
                // 等 Angular model 同步到 hidden 提交字段（最多 1.5s），同步后再点登录
                function waitSync(round){
                  var hu = document.querySelector('input[name=username][type=hidden]');
                  var hp = document.querySelector('input[name=password][type=hidden]');
                  var ok = true;
                  if (hu) ok = ok && (hu.value === u);
                  if (hp) ok = ok && (hp.value === p);
                  if (ok || round >= 5) { doClick(); }
                  else { setTimeout(function(){ waitSync(round + 1); }, 300); }
                }
                function doClick(){
                  var btn = document.querySelector('button[type=submit]')
                    || document.querySelector('.login-btn, button.login-btn, #loginBtn, #submitBtn, ion-button[type="submit"], .van-button--primary');
                  if (!btn) {
                    var all = document.querySelectorAll('button, a, input[type=submit], [role="button"]');
                    for (var i = 0; i < all.length; i++) {
                      var b = all[i];
                      if (/登录|登\s*录|sign\s*in|submit|login/i.test(b.textContent || b.value || '')) { btn = b; break; }
                    }
                  }
                  if (btn) {
                    try { if (btn.disabled) btn.disabled = false; } catch(e) {}
                    try {
                      btn.dispatchEvent(new MouseEvent('mousedown', {bubbles: true, cancelable: true}));
                      btn.dispatchEvent(new MouseEvent('mouseup', {bubbles: true, cancelable: true}));
                    } catch(e) {}
                    // 移动端（Ionic/Vant）按钮常只响应 touch 事件
                    try {
                      btn.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true}));
                      btn.dispatchEvent(new TouchEvent('touchend', {bubbles: true, cancelable: true}));
                    } catch(e) {}
                    try { btn.click(); window.__autoLoginSubmitted = true; } catch(e) {}
                  } else {
                    var form = document.querySelector('form');
                    if (form) { try { form.requestSubmit(); window.__autoLoginSubmitted = true; } catch(e) {} }
                  }
                }
                waitSync(0);
              }, 500);
              return 'will-submit';
        """.trimIndent() else "return 'filled-only';"
        return """
            (function(u, p){
              var pw = document.querySelector('input[type=password]');
              if (!pw) return 'no-login-page';
              var uname = document.querySelector('input[name=username]:not([type=hidden])')
                || document.querySelector('input[id="nameInput"]')
                || document.querySelector('input[type=text][name*="user" i]')
                || document.querySelector('input[type=text]')
                || document.querySelector('input[placeholder*="学工号" i]')
                || document.querySelector('input[placeholder*="账号" i]')
                || document.querySelector('input:not([type]):not([type=hidden])');
              if (!uname) return 'no-uname';
              // 手机端默认可能停在"短信验证码"登录方式：先切到"用户名密码/账号密码"选项卡，
              // 密码登录表单才会真正可提交（填表在前、点按钮在 500ms 后）
              (function(){
                var tabs = document.querySelectorAll('.van-tab, [role="tab"], .tab, ion-segment-button, .segment-button, .login-type, [class*="login"][class*="tab"], [class*="type"]');
                for (var i = 0; i < tabs.length; i++) {
                  var tx = (tabs[i].textContent || '') + ' ' + (tabs[i].getAttribute('class') || '');
                  if (/用户名密码|账号密码|密码登录|账号登录/i.test(tx) && !/短信|验证码|扫码/i.test(tx)) {
                    try { tabs[i].click(); } catch(e) {}
                    break;
                  }
                }
              })();
              function setVal(el, v){
                var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                setter.call(el, v);
                var evs = ['input', 'change', 'keyup'];
                for (var i = 0; i < evs.length; i++) {
                  var ev;
                  if (evs[i] === 'keyup') {
                    try { ev = new KeyboardEvent('keyup', {bubbles: true, key: v.slice(-1)}); } catch(e) { ev = new Event('keyup', {bubbles: true}); }
                  } else {
                    ev = new Event(evs[i], {bubbles: true});
                  }
                  el.dispatchEvent(ev);
                }
                try { el.dispatchEvent(new Event('ionInput', {bubbles: true, cancelable: true})); } catch(e) {}
              }
              setVal(uname, u);
              setVal(pw, p);
              $submitJs
            })($u, $p)
        """.trimIndent()
    }

    /**
     * 统一认证按屏幕尺寸判断移动/桌面端。登录页注入：把 viewport 扩宽到 1440，
     * 让响应式布局切换为电脑端（账号密码表单更完整、界面更接近桌面浏览器），
     * 然后触发 resize 让 Ionic/Angular 重新计算平台标记。
     */
    val JS_SSO_DESKTOP_VIEW: String = """
        (function(){
          var m = document.querySelector('meta[name=viewport]');
          if (m) {
            m.setAttribute('content', 'width=1440, initial-scale=1');
          } else {
            var nm = document.createElement('meta');
            nm.name = 'viewport';
            nm.content = 'width=1440, initial-scale=1';
            document.head.appendChild(nm);
          }
          try { window.dispatchEvent(new Event('resize')); } catch(e) {}
          try { window.dispatchEvent(new Event('orientationchange')); } catch(e) {}
          return 'viewport-ok';
        })()
    """.trimIndent()

    /**
     * 自动输入 4 位签到码：模拟 touch 事件点击 Vant 数字键盘按键。
     * v1.5 重写为「盲输 + 整体校验 + 整体重试」：
     * - 不再依赖中间步计数前进（旧版若计数误判会反复重按同一键，导致 2345→2222）；
     * - 每个键间隔 300ms 依次点击，全部输完后再校验总数；
     * - 总数不对才清残留整体重试一次（保留最后一次校验错误信息）；
     * - 注入前先确保键盘显示（Vant 键盘未显示时按键 DOM 不存在，先 click 密码框拉起）。
     */
    fun jsFillCode(code: String): String {
        return """
            (function(code){
              window.__fillDone = null;
              window.__fillFail = null;

              function visible(el){
                try {
                  var r = el.getBoundingClientRect();
                  return r.width > 0 && r.height > 0;
                } catch(e) { return true; }
              }

              // 找数字键：优先 .van-key 键体，wrapper 兜底；排除删除/清空键
              function findKey(d){
                var all = document.querySelectorAll('.van-key__wrapper, .van-key');
                var cands = [];
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  if (el.classList && (el.classList.contains('van-key--delete') || el.classList.contains('van-key--clear'))) continue;
                  var t = (el.textContent || '').replace(/\s+/g, '');
                  if (t === String(d)) cands.push(el);
                }
                if (!cands.length) return null;
                for (var j = 0; j < cands.length; j++) {
                  var el = cands[j];
                  if (el.classList && el.classList.contains('van-key') && visible(el)) return el;
                }
                for (var k = 0; k < cands.length; k++) { if (visible(cands[k])) return cands[k]; }
                return cands[0];
              }

              function findDelete(){
                var els = document.querySelectorAll('.van-key--delete');
                for (var i = 0; i < els.length; i++) { if (visible(els[i])) return els[i]; }
                return els.length ? els[0] : null;
              }

              // 已填位数：圆点 icon（掩码模式）或每格最后一个数字（明文模式，排除光标干扰）
              function filledCount(){
                var icons = document.querySelectorAll('.van-password-input__item-icon');
                if (icons.length > 0) {
                  var n = 0;
                  for (var i = 0; i < icons.length; i++) {
                    var item = (icons[i].closest && icons[i].closest('.van-password-input__item')) || icons[i].parentElement;
                    if (visible(item)) n++;
                  }
                  return n;
                }
                var items = document.querySelectorAll('.van-password-input__item');
                var n2 = 0;
                for (var j = 0; j < items.length; j++) {
                  var t = (items[j].textContent || '').replace(/[^0-9]/g, '');
                  if (t.length > 0) n2++;
                }
                return n2;
              }

              function makeTouch(el){
                try {
                  if (window.Touch) {
                    return new Touch({identifier: (Date.now() & 0xffff), target: el, clientX: 50, clientY: 50, radiusX: 2, radiusY: 2, force: 1});
                  }
                } catch(e) {}
                return null;
              }

              function press(el){
                var t = makeTouch(el);
                if (t) {
                  el.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true, touches: [t], targetTouches: [t], changedTouches: [t]}));
                  el.dispatchEvent(new TouchEvent('touchend', {bubbles: true, cancelable: true, touches: [], targetTouches: [], changedTouches: [t]}));
                  return true;
                }
                el.click();
                return true;
              }

              function pressKey(key){
                // 事件可能绑在键体或 wrapper 上：按可见键体（事件冒泡会到达 wrapper）
                if (visible(key)) { press(key); return; }
                var w = key.closest ? key.closest('.van-key__wrapper') : null;
                if (w && visible(w)) { press(w); return; }
                press(key);
              }

              // 盲输：逐键点击，间隔 180ms（签到窗口仅 15 秒，越快越好；180ms 足够 Vant 处理 touch）
              function blindFill(i){
                if (i >= code.length) {
                  setTimeout(function(){
                    var n = filledCount();
                    if (n === code.length) { window.__fillDone = true; }
                    else {
                      window.__fillDone = 'fail';
                      window.__fillFail = 'count:' + n + ':' + code.length;
                    }
                  }, 300);
                  return;
                }
                var d = code.charAt(i);
                var key = findKey(d);
                if (!key) {
                  window.__fillDone = 'fail';
                  window.__fillFail = 'no-key:' + d;
                  return;
                }
                pressKey(key);
                i++;
                setTimeout(function(){ blindFill(i); }, 180);
              }

              // 清残留后整体重试（最多整体 2 轮）
              function clearAndRetry(round){
                var cleared = 0;
                function doClear(){
                  var n = filledCount();
                  if (n <= 0 || cleared >= 6) {
                    if (round < 2) blindFill(0);
                    else {
                      window.__fillDone = 'fail';
                      window.__fillFail = 'retry-limit:' + n;
                    }
                    return;
                  }
                  var del = findDelete();
                  if (!del) { blindFill(0); return; }
                  press(del);
                  cleared++;
                  setTimeout(doClear, 150);
                }
                doClear();
              }

              // 先确保键盘显示（Vant 键盘未显示时按键 DOM 不存在）
              function ensureKb(round){
                var kb = document.querySelector('.van-number-keyboard');
                var shown = kb && kb.getBoundingClientRect().width > 0 && kb.getBoundingClientRect().height > 0;
                if (shown) { clearAndRetry(0); return; }
                var item = document.querySelector('.van-password-input__item');
                if (item) { try { item.click(); } catch(e) {} }
                if (round >= 3) { clearAndRetry(0); return; }
                setTimeout(function(){ ensureKb(round + 1); }, 250);
              }
              setTimeout(function(){ ensureKb(0); }, 100);
            })("$code")
        """.trimIndent()
    }

    /** 查询自动输入结果并复位，返回 ok / fail:原因 / 空（未完成） */
    val JS_CHECK_FILL: String = """
        (function(){
          var d = window.__fillDone;
          var f = window.__fillFail || '';
          window.__fillDone = null;
          window.__fillFail = null;
          window.__fillStep = 0;
          window.__fillRetry = 0;
          if (d === true) return 'ok';
          if (d === 'fail') return 'fail:' + f;
          return '';
        })()
    """.trimIndent()

    /**
     * 逐位注入：手动键盘每按一个数字，立即把该数字输入到网页密码框（不等待输满）。
     * 键盘未显示时先点击密码框拉起；找到数字键后派发 touch 序列（Vant 只响应 touch）。
     * 输入期间由用户在弹层里逐位操作，网页密码框实时逐位亮起（明文），输满 4 位页面自动提交。
     */
    fun jsFillDigit(digit: String): String {
        val d = JSONObject.quote(digit)
        return """
            (function(d){
              function ensureKb(round){
                var kb = document.querySelector('.van-number-keyboard');
                var shown = kb && kb.getBoundingClientRect().width > 0 && kb.getBoundingClientRect().height > 0;
                if (shown) { doPress(); return; }
                var item = document.querySelector('.van-password-input__item');
                if (item) { try { item.click(); } catch(e){} }
                if (round >= 3) { doPress(); return; }
                setTimeout(function(){ ensureKb(round + 1); }, 250);
              }
              function doPress(){
                var all = document.querySelectorAll('.van-key__wrapper, .van-key');
                var cands = [];
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  if (el.classList && (el.classList.contains('van-key--delete') || el.classList.contains('van-key--clear'))) continue;
                  var t = (el.textContent || '').replace(/\s+/g, '');
                  if (t === d) cands.push(el);
                }
                if (!cands.length) { window.__digitDone = 'fail'; return; }
                var key = null;
                for (var j = 0; j < cands.length; j++) {
                  var c = cands[j];
                  if (c.classList && c.classList.contains('van-key')) { key = c; break; }
                }
                if (!key) {
                  for (var k = 0; k < cands.length; k++) {
                    if (cands[k].getBoundingClientRect().width > 0) { key = cands[k]; break; }
                  }
                }
                if (!key) key = cands[0];
                var tch;
                try { if (window.Touch) tch = new Touch({identifier: (Date.now() & 0xffff), target: key, clientX: 50, clientY: 50, radiusX: 2, radiusY: 2, force: 1}); } catch(e) {}
                if (tch) {
                  key.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true, touches: [tch], targetTouches: [tch], changedTouches: [tch]}));
                  key.dispatchEvent(new TouchEvent('touchend', {bubbles: true, cancelable: true, touches: [], targetTouches: [], changedTouches: [tch]}));
                } else {
                  key.click();
                }
                window.__digitDone = true;
              }
              setTimeout(function(){ ensureKb(0); }, 100);
            })($d)
        """.trimIndent()
    }

    /**
     * 清空网页密码框残留（打开手动键盘时调用，重新开始输入）：
     * 拉起键盘后连按删除键最多 4 次。
     */
    val JS_CLEAR_SITE_CODE: String = """
        (function(){
          function press(el){
            var tch;
            try { if (window.Touch) tch = new Touch({identifier: (Date.now() & 0xffff), target: el, clientX: 50, clientY: 50, radiusX: 2, radiusY: 2, force: 1}); } catch(e) {}
            if (tch) {
              el.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true, touches: [tch], targetTouches: [tch], changedTouches: [tch]}));
              el.dispatchEvent(new TouchEvent('touchend', {bubbles: true, cancelable: true, touches: [], targetTouches: [], changedTouches: [tch]}));
            } else {
              el.click();
            }
          }
          function findDel(){
            var dels = document.querySelectorAll('.van-key--delete');
            for (var i = 0; i < dels.length; i++) {
              if (dels[i].getBoundingClientRect().width > 0) return dels[i];
            }
            return dels.length ? dels[0] : null;
          }
          function ensureKb(round){
            var kb = document.querySelector('.van-number-keyboard');
            if (kb && kb.getBoundingClientRect().width > 0 && kb.getBoundingClientRect().height > 0) { doClear(); return; }
            var item = document.querySelector('.van-password-input__item');
            if (item) { try { item.click(); } catch(e){} }
            if (round >= 3) { doClear(); return; }
            setTimeout(function(){ ensureKb(round + 1); }, 250);
          }
          function doClear(){
            var del = findDel();
            if (!del) return;
            var n = 0;
            function tick(){
              if (n >= 4) return;
              press(del);
              n++;
              setTimeout(tick, 150);
            }
            tick();
          }
          setTimeout(function(){ ensureKb(0); }, 100);
        })()
    """.trimIndent()

    /**
     * 收起网页自带的 Vant 数字键盘：
     * Vant NumberKeyboard 在 hideOnClickOutside 时监听 document 的 touchstart，
     * 点键盘外任意处即收起，所以在 body 上派发一次合成 touchstart。
     * 注意：签到页键盘的关闭按钮文案是"签到"（点击会提交），绝不能点关闭按钮。
     */
    val JS_CLOSE_SITE_KEYBOARD: String = """
        (function(){
          var kb = document.querySelector('.van-number-keyboard');
          if (!kb) return 'none';
          try {
            var r = kb.getBoundingClientRect();
            if (r.width <= 0 || r.height <= 0) return 'hidden';
          } catch(e) {}
          try {
            if (window.Touch) {
              var t = new Touch({identifier: 2, target: document.body, clientX: 5, clientY: 5, radiusX: 1, radiusY: 1, force: 1});
              document.body.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true, touches: [t], targetTouches: [t], changedTouches: [t]}));
              return 'tap-outer';
            }
          } catch(e) {}
          try {
            var item = document.querySelector('.van-password-input');
            if (item) {
              item.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, cancelable: true}));
            }
          } catch(e) {}
          return 'fallback';
        })()
    """.trimIndent()
}
