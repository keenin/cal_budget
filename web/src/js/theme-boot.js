/* Applies the last chosen appearance and text size before the first paint.
   IndexedDB remains the source of truth for the budget. These keys only
   avoid a flash of the wrong colors or size. */
(function () {
  var root = document.documentElement;
  try {
    var theme = localStorage.getItem('calbudget-theme');
    if (theme === 'light' || theme === 'dark') root.dataset.theme = theme;
    var size = localStorage.getItem('calbudget-text-size');
    if (size === 'small' || size === 'large' || size === 'xlarge') root.dataset.textSize = size;
  } catch (error) {
    /* Private mode can block localStorage. The app still loads from IndexedDB. */
  }
  var dark = root.dataset.theme === 'dark' ||
    (root.dataset.theme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  var color = dark ? '#121412' : '#F7F6F3';
  var metas = document.querySelectorAll('meta[name="theme-color"]');
  for (var i = 0; i < metas.length; i += 1) metas[i].setAttribute('content', color);
  var active = document.createElement('meta');
  active.name = 'theme-color';
  active.content = color;
  active.setAttribute('data-active', 'true');
  document.head.appendChild(active);
})();
