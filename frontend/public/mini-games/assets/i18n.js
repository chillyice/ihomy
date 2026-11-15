/* Minimal bilingual helper shared by every game in this repo.
 * Usage:
 *   <script src="../assets/i18n.js"></script>
 *   var T = LiteI18N.create({ key: { en: '...', zh: '...' } });
 *   T.start();                       // mounts the switcher and applies the language
 *   T.t('key');                      // translate in JS
 *   T.onChange(function (lang) {});  // re-render dynamic text
 * Markup hooks: data-i18n, data-i18n-html, data-i18n-title, data-i18n-ph
 * Switcher host: <div class="lang-switch" data-lang-switch></div>
 */
(function (global) {
  'use strict';

  var STORE_KEY = 'litegame_lang';
  var STYLE_ID = 'litegame-i18n-style';

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var css =
      '.lang-switch{display:flex;gap:8px;align-items:center;margin-bottom:14px;justify-content:flex-start}' +
      '.lang-switch a{font-size:13px;color:inherit;opacity:.55;text-decoration:none;' +
      'padding:2px 9px;border:1px solid currentColor;border-radius:999px;line-height:1.5}' +
      '.lang-switch a:hover{opacity:.85}' +
      '.lang-switch a.active{opacity:1;font-weight:700}';
    var el = document.createElement('style');
    el.id = STYLE_ID;
    el.textContent = css;
    document.head.appendChild(el);
  }

  function detect() {
    try {
      var saved = localStorage.getItem(STORE_KEY);
      if (saved === 'zh' || saved === 'en') return saved;
    } catch (e) {}
    return (navigator.language || '').toLowerCase().indexOf('zh') === 0 ? 'zh' : 'en';
  }

  function create(dict) {
    var current = detect();
    var listeners = [];

    function save(lang) {
      try { localStorage.setItem(STORE_KEY, lang); } catch (e) {}
    }

    // Translate a key, interpolating {name} placeholders from vars when given.
    function t(key, vars) {
      var entry = dict[key];
      if (!entry) return key;
      var text = entry[current] != null ? entry[current] : entry.en;
      if (text == null) return key;
      if (vars) {
        Object.keys(vars).forEach(function (k) {
          text = text.split('{' + k + '}').join(vars[k]);
        });
      }
      return text;
    }

    function each(attr, fn) {
      var nodes = document.querySelectorAll('[' + attr + ']');
      for (var i = 0; i < nodes.length; i++) fn(nodes[i]);
    }

    function apply() {
      each('data-i18n', function (el) {
        el.textContent = t(el.getAttribute('data-i18n'));
      });
      each('data-i18n-html', function (el) {
        el.innerHTML = t(el.getAttribute('data-i18n-html'));
      });
      each('data-i18n-title', function (el) {
        el.title = t(el.getAttribute('data-i18n-title'));
      });
      each('data-i18n-ph', function (el) {
        el.placeholder = t(el.getAttribute('data-i18n-ph'));
      });
      document.documentElement.lang = current === 'zh' ? 'zh-CN' : 'en';
      syncSwitch();
      listeners.forEach(function (fn) { fn(current); });
    }

    function syncSwitch() {
      var links = document.querySelectorAll('[data-lang-switch] a');
      for (var i = 0; i < links.length; i++) {
        var a = links[i];
        if (a.getAttribute('data-lang') === current) a.classList.add('active');
        else a.classList.remove('active');
      }
    }

    function set(lang) {
      if (lang !== 'zh' && lang !== 'en') return;
      if (lang === current) return;
      current = lang;
      save(lang);
      apply();
    }

    function mount() {
      var hosts = document.querySelectorAll('[data-lang-switch]');
      for (var i = 0; i < hosts.length; i++) {
        var box = hosts[i];
        box.innerHTML = '';
        ['en', 'zh'].forEach(function (code) {
          var a = document.createElement('a');
          a.href = '#';
          a.setAttribute('data-lang', code);
          a.textContent = code === 'en' ? 'English' : '简体中文';
          a.addEventListener('click', function (ev) {
            ev.preventDefault();
            set(code);
          });
          box.appendChild(a);
        });
      }
    }

    return {
      t: t,
      set: set,
      apply: apply,
      onChange: function (fn) { listeners.push(fn); },
      start: function () {
        injectStyle();
        mount();
        apply();
      },
      get lang() { return current; }
    };
  }

  global.LiteI18N = { create: create };
})(window);
