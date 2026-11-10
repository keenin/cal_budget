/* Applies the last chosen appearance before the first paint. IndexedDB remains
   the source of truth; this only avoids a flash of the wrong colors. */
(function () {
  try {
    var theme = localStorage.getItem('calbudget-theme');
    if (theme === 'light' || theme === 'dark') {
      document.documentElement.dataset.theme = theme;
    }
  } catch (error) {
    /* Private mode can block localStorage. The app still loads from IndexedDB. */
  }
})();
