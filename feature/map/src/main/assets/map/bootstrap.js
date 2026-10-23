// Installed before the bundle so a failed script load or initialization is reported to Android.
window.reportMapEvent = event => window.Android?.postMessage(JSON.stringify(event));
window.addEventListener('error', event => {
  if (event.target instanceof HTMLScriptElement || event.error) {
    window.reportMapEvent({type: 'failed'});
  }
}, true);
window.addEventListener('unhandledrejection', () => window.reportMapEvent({type: 'failed'}));
