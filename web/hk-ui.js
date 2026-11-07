/* HK Downloader Pro - Futuristic UI Controller
   Connects hk-theme.css + hk-reference-markup.html with live video resolution & download engine. */
(function (w) {
  var $ = function (id) { return document.getElementById(id); };
  var root = function () { return $('flow') || document.querySelector('.hk-app'); };

  function countUp(el, to, unit, ms) {
    var t0 = null; unit = unit || ''; ms = ms || 1500;
    (function f(t) {
      if (!t0) t0 = t;
      var p = Math.min((t - t0) / ms, 1), v = to * (1 - Math.pow(1 - p, 3));
      el.textContent = (to % 1 ? v.toFixed(1) : Math.round(v)) + unit;
      if (p < 1) requestAnimationFrame(f);
    })(performance.now());
  }

  var HKUI = {
    selectedFormatIndex: 0,
    currentData: null,

    /* Step 1-2: after user pastes a link */
    status: function (text, ok) {            // "READING LINK...", "VIDEO FOUND"
      var el = $('fst'); if (!el) return;
      el.className = 'fst' + (ok ? ' ok' : '');
      el.innerHTML = ok ? text : '<span class="spin"></span>' + text;
    },
    showPreview: function (info) {           // {title, sizeMB, thumbnail, formats, qualities:["720p HD","480p"], selected:0}
      this.currentData = info;
      this.selectedFormatIndex = info.selected || 0;
      var pv = $('pv'); if (!pv) return;

      var titleEl = pv.querySelector('.dl-t') || $('pvTitle');
      if (titleEl) titleEl.textContent = info.title;

      var subEl = pv.querySelector('.s') || $('pvSubtitle');
      var firstQuality = (info.qualities && info.qualities[0]) ? info.qualities[0] : '';
      if (subEl) subEl.textContent = 'VIDEO FOUND · ' + (info.sizeMB && info.sizeMB !== 'HD' ? info.sizeMB + ' MB' : firstQuality || 'HD');

      var thumbEl = pv.querySelector('.th') || $('pvThumb');
      if (thumbEl) {
        if (info.thumbnail) {
          thumbEl.innerHTML = '<img src="' + info.thumbnail + '" alt="thumb">';
        } else {
          thumbEl.innerHTML = '&#9654;';
        }
      }

      var q = pv.querySelector('.q') || $('pvQualities');
      if (q && info.qualities) {
        q.innerHTML = info.qualities.map(function (n, i) {
          return '<span data-i="' + i + '"' + (i === (info.selected || 0) ? ' class="on"' : '') + '>' + n + '</span>';
        }).join('');

        q.querySelectorAll('span').forEach(function(sp) {
          sp.addEventListener('click', function() {
            q.querySelectorAll('span').forEach(function(s) { s.classList.remove('on'); });
            sp.classList.add('on');
            HKUI.selectedFormatIndex = parseInt(sp.getAttribute('data-i'), 10) || 0;
            if (info.formats && info.formats[HKUI.selectedFormatIndex]) {
              var f = info.formats[HKUI.selectedFormatIndex];
              var qualityName = info.qualities ? info.qualities[HKUI.selectedFormatIndex] : '';
              var mb = f.sizeBytes ? (f.sizeBytes / 1048576).toFixed(1) + ' MB' : (f.size ? (f.size / 1048576).toFixed(1) + ' MB' : '');
              if (subEl) subEl.textContent = 'READY · ' + (qualityName ? qualityName + (mb ? ' (' + mb + ')' : '') : (mb || 'HD'));
            }
          });
        });
      }
      pv.classList.add('show');
      pv.classList.remove('hidden');
    },
    hidePreview: function () {
      var pv = $('pv'); if (pv) { pv.classList.remove('show'); pv.classList.add('hidden'); }
    },
    /* Step 3: download starts -> bottom sheet slides up */
    openSheet: function (info) {             // {title, quality, totalMB, thumbnail}
      this.totalMB = info.totalMB || 0;
      if ($('sttl')) $('sttl').textContent = 'Downloading media';
      if ($('stt')) $('stt').textContent = 'Live';
      if ($('sheetMediaTitle')) $('sheetMediaTitle').textContent = info.title || 'Media file';
      if ($('sheetThumb')) {
        if (info.thumbnail) {
          $('sheetThumb').innerHTML = '<img src="' + info.thumbnail + '" alt="thumb">';
        } else {
          $('sheetThumb').innerHTML = '&#9654;';
        }
      }
      if ($('sheetTags')) {
        $('sheetTags').innerHTML = '<span>' + (info.quality || 'HD MP4') + '</span><span>High Speed</span>';
      }
      this.progress({ percent: 0, doneMB: 0, speedMB: 0, etaSec: null });
      var saf = $('sheetAdFrame');
      if (saf) saf.src = '/ad-300x250?r=' + Date.now();
      var r = root();
      if (r) r.classList.add('open');
    },
    progress: function (p) {                 // {percent, doneMB, speedMB, etaSec}
      if ($('fl')) $('fl').style.width = p.percent + '%';
      if ($('pp')) $('pp').textContent = Math.round(p.percent) + '%';
      if ($('sz')) $('sz').textContent = (p.doneMB || 0).toFixed(1) + ' / ' + (this.totalMB || 0).toFixed(1) + ' MB';
      if ($('sp')) $('sp').textContent = (p.speedMB || 0).toFixed(1) + ' MB/s';
      if ($('eta')) $('eta').textContent = p.etaSec == null ? '--' : Math.max(1, Math.round(p.etaSec)) + 's';
    },
    complete: function (info) {
      if ($('sttl')) $('sttl').textContent = 'Download complete';
      if ($('stt')) $('stt').textContent = 'Saved';
      if ($('sp')) $('sp').textContent = 'Done';
      if ($('eta')) $('eta').textContent = '0s';
      if ($('fl')) $('fl').style.width = '100%';
      if ($('pp')) $('pp').textContent = '100%';

      // Complete screen modal with 100% circular glowing ring
      var comp = $('completeView');
      if (comp) {
        if ($('compPercent')) $('compPercent').textContent = '100%';
        if ($('compSavedText') && info) $('compSavedText').textContent = (info.sizeMB ? info.sizeMB.toFixed(1) : (this.totalMB || 5.3).toFixed(1)) + ' MB saved';
        if ($('compTitle') && info) $('compTitle').textContent = info.title || 'Downloaded media';
        if ($('compThumb') && info) {
          if (info.thumbnail) $('compThumb').innerHTML = '<img src="' + info.thumbnail + '" alt="thumb">';
          else $('compThumb').innerHTML = '&#9654;';
        }
        if ($('compTags') && info) {
          $('compTags').innerHTML = '<span>' + (info.quality || '1080p HD') + '</span><span>No watermark</span><span>' + (info.ext ? info.ext.toUpperCase() : 'MP4') + '</span>';
        }
        var caf = $('compAdFrame');
        if (caf) caf.src = '/ad-320x50?r=' + Date.now();
        comp.classList.remove('hidden');
      }
    },
    fail: function (msg) {
      if ($('sttl')) $('sttl').textContent = msg || 'Download failed';
      if ($('stt')) $('stt').textContent = 'Failed';
    },
    closeSheet: function () {
      var r = root();
      if (r) r.classList.remove('open');
    },
    /* Helpers */
    countUp: countUp,
    countAll: function () {
      document.querySelectorAll('[data-n]').forEach(function (el) {
        countUp(el, parseFloat(el.dataset.n), el.dataset.u || '');
      });
    }
  };
  w.HKUI = HKUI;
})(window);
