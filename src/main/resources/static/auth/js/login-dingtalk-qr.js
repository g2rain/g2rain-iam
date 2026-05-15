/**
 * 登录页钉钉 Tab：懒加载 ddLogin.js、申请 bootstrap、渲染二维码、监听 postMessage 后整页跳转。
 * 依赖页面上的 [data-dingtalk-qr-init]、#clientId / #redirectUri / #state、#dingtalk-qr-status。
 */
(function () {
    var wrap = document.querySelector('[data-dingtalk-qr-init]');
    if (!wrap) {
        return;
    }
    var bootstrapUrl = wrap.getAttribute('data-bootstrap-url');
    var bindMode = wrap.getAttribute('data-bind-mode') || '';
    var qrId = wrap.getAttribute('data-qr-container-id') || 'dingtalk-qr-container';
    var statusEl = document.getElementById('dingtalk-qr-status');
    var ddLoginSrc = 'https://g.alicdn.com/dingding/dinglogin/0.0.5/ddLogin.js';

    function setStatus(t) {
        if (statusEl) {
            statusEl.textContent = t;
        }
    }

    function loadScript(src) {
        return new Promise(function (resolve, reject) {
            var s = document.createElement('script');
            s.src = src;
            s.async = true;
            s.onload = function () {
                resolve();
            };
            s.onerror = function () {
                reject(new Error('ddLogin script load failed'));
            };
            document.head.appendChild(s);
        });
    }

    function extractLoginTmpCode(data) {
        if (data == null) {
            return null;
        }
        if (typeof data === 'string') {
            return data;
        }
        if (typeof data === 'object') {
            if (data.loginTmpCode) {
                return String(data.loginTmpCode);
            }
            if (data.tmpCode) {
                return String(data.tmpCode);
            }
        }
        return null;
    }

    function runQrBootstrap() {
        return fetch(bootstrapUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({
                bindMode: bindMode,
                clientId: document.getElementById('clientId').value,
                redirectUri: document.getElementById('redirectUri').value,
                state: document.getElementById('state').value || ''
            })
        })
            .then(function (r) {
                return r.json();
            })
            .then(function (json) {
                if (!json || !json.data || !json.data.gotoUrl) {
                    setStatus('无法准备钉钉扫码，请使用「浏览器跳转钉钉登录」。');
                    return;
                }
                var gotoPlain = json.data.gotoUrl;
                if (typeof window.DDLogin !== 'function') {
                    setStatus('钉钉扫码组件未就绪，请使用「浏览器跳转钉钉登录」。');
                    return;
                }
                function onMsg(ev) {
                    if (ev.origin !== 'https://login.dingtalk.com') {
                        return;
                    }
                    var code = extractLoginTmpCode(ev.data);
                    if (!code) {
                        return;
                    }
                    window.removeEventListener('message', onMsg, false);
                    try {
                        var u = new URL(gotoPlain);
                        u.searchParams.set('loginTmpCode', code);
                        window.location.assign(u.toString());
                    } catch (e) {
                        setStatus('扫码成功，但跳转失败，请使用「浏览器跳转钉钉登录」。');
                    }
                }
                window.addEventListener('message', onMsg, false);
                window.DDLogin({
                    id: qrId,
                    goto: encodeURIComponent(gotoPlain),
                    style: 'border:none;background-color:#FFFFFF;',
                    width: '300',
                    height: '300'
                });
                setStatus('请使用钉钉扫描上方二维码');
            });
    }

    window.__g2rainInitDingTalkQr = function () {
        if (window.__g2rainDingTalkQrStarted) {
            return;
        }
        window.__g2rainDingTalkQrStarted = true;
        setStatus('正在加载钉钉扫码…');
        loadScript(ddLoginSrc)
            .then(runQrBootstrap)
            .catch(function () {
                setStatus('加载钉钉扫码失败，请使用「浏览器跳转钉钉登录」。');
            });
    };

    var tabBtns = document.querySelectorAll('[data-login-tab]');
    var panelAccount = document.getElementById('login-tab-panel-account');
    var panelDingtalk = document.getElementById('login-tab-panel-dingtalk');
    if (tabBtns.length && panelAccount && panelDingtalk) {
        tabBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var name = btn.getAttribute('data-login-tab');
                tabBtns.forEach(function (b) {
                    var on = b.getAttribute('data-login-tab') === name;
                    b.classList.toggle('login-tab-active', on);
                    b.setAttribute('aria-selected', on ? 'true' : 'false');
                });
                panelAccount.classList.toggle('login-tab-panel-active', name === 'account');
                panelDingtalk.classList.toggle('login-tab-panel-active', name === 'dingtalk');
                if (name === 'dingtalk') {
                    window.__g2rainInitDingTalkQr();
                }
            });
        });
    }
})();
