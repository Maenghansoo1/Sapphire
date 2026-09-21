// HA'WE Cordova app bootstrap
document.addEventListener('deviceready', onDeviceReady, false);

function onDeviceReady() {
  console.log("HA'WE running on " + cordova.platformId + " @ " + cordova.version);

  // Example: handle Android hardware back button to go to previous page
  document.addEventListener('backbutton', function (e) {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      navigator.app.exitApp();
    }
  }, false);
}
