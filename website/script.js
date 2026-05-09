document.querySelectorAll('[data-year]').forEach((el) => {
  el.textContent = new Date().getFullYear();
});

const checkForm = document.getElementById('version-checker-form');
if (checkForm) {
  checkForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const latest = document.getElementById('latestVersion').value.trim();
    const installed = document.getElementById('installedVersion').value.trim();
    const result = document.getElementById('versionResult');

    if (!latest || !installed) {
      result.textContent = 'Enter both latest and installed versions first.';
      return;
    }
    if (latest === installed) {
      result.textContent = `✅ You're up to date on ${installed}.`;
    } else {
      result.textContent = `⬆️ Update available: installed ${installed}, latest ${latest}. Back up your world before upgrading.`;
    }
  });
}
