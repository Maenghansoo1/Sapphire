// HA'WE 새 화면(www/screens/*) 공통 인터랙션
(function () {
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  // 뒤로가기: 히스토리가 있으면 back, 없으면 href로 이동
  $$('[data-back]').forEach(function (el) {
    el.addEventListener('click', function (e) {
      if (window.history.length > 1) {
        e.preventDefault();
        window.history.back();
      }
    });
  });

  // 단일 선택 그룹 (Chip, 캘린더 날짜 등): data-select="클래스명"
  $$('[data-select]').forEach(function (group) {
    var cls = group.getAttribute('data-select') || 'is-selected';
    group.addEventListener('click', function (e) {
      var item = e.target.closest('[data-option]');
      if (!item || !group.contains(item) || item.hasAttribute('href')) return;
      $$('[data-option]', group).forEach(function (o) { o.classList.toggle(cls, o === item); });
      group.dispatchEvent(new CustomEvent('select', { detail: item }));
    });
  });

  // 캐릭터 고르기: 선택 시 미리보기 이미지·이름 갱신
  $$('[data-char-grid]').forEach(function (grid) {
    var preview = document.querySelector(grid.getAttribute('data-char-grid'));
    grid.addEventListener('select', function (e) {
      var img = e.detail.querySelector('img');
      if (!preview || !img || !e.detail.dataset.name) return;
      preview.querySelector('img').src = img.getAttribute('src');
      var name = preview.querySelector('[data-char-name]');
      if (name) name.textContent = e.detail.dataset.name;
    });
  });

  // 전체 동의 체크박스
  $$('[data-check-all]').forEach(function (all) {
    var items = $$(all.getAttribute('data-check-all'));
    all.addEventListener('change', function () {
      items.forEach(function (cb) { cb.checked = all.checked; });
    });
    items.forEach(function (cb) {
      cb.addEventListener('change', function () {
        all.checked = items.every(function (i) { return i.checked; });
      });
    });
  });

  // 체크되면 버튼 활성화: data-enables="#btn" data-enabled-class="btn-danger"
  $$('[data-enables]').forEach(function (cb) {
    var btn = document.querySelector(cb.getAttribute('data-enables'));
    var on = cb.getAttribute('data-enabled-class');
    var sync = function () {
      btn.classList.toggle('is-disabled', !cb.checked);
      if (on) btn.classList.toggle(on, cb.checked);
    };
    cb.addEventListener('change', sync);
    sync();
  });

  // 글자 수 카운터: <input maxlength="10" data-count="#counter">
  $$('[data-count]').forEach(function (input) {
    var out = document.querySelector(input.getAttribute('data-count'));
    var max = input.getAttribute('maxlength');
    var sync = function () { out.textContent = input.value.length + ' / ' + max; };
    input.addEventListener('input', sync);
    sync();
  });

  // 초대 코드 6칸: 입력하면 다음 칸으로, 지우면 이전 칸으로
  $$('[data-code]').forEach(function (wrap) {
    var cells = $$('input', wrap);
    cells.forEach(function (cell, i) {
      cell.addEventListener('input', function () {
        cell.value = cell.value.replace(/[^0-9a-z]/gi, '').slice(-1).toUpperCase();
        if (cell.value && cells[i + 1]) cells[i + 1].focus();
      });
      cell.addEventListener('keydown', function (e) {
        if (e.key === 'Backspace' && !cell.value && cells[i - 1]) cells[i - 1].focus();
      });
      cell.addEventListener('paste', function (e) {
        var text = (e.clipboardData.getData('text') || '').replace(/[^0-9a-z]/gi, '').toUpperCase();
        if (!text) return;
        e.preventDefault();
        cells.forEach(function (c, j) { c.value = text[j] || ''; });
        (cells[Math.min(text.length, cells.length - 1)]).focus();
      });
    });
  });

  // 텍스트 복사
  $$('[data-copy]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.getAttribute('data-copy');
      if (navigator.clipboard) navigator.clipboard.writeText(text);
    });
  });
})();
