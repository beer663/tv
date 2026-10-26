# TV Browser (GeckoView + uBlock Origin)
Desktop-class Firefox engine, real uBlock Origin, popup blocking, D-pad pointer + mouse, bookmarks, search.

## Build on GitHub (no install)
Upload everything (including the hidden .github folder) to a new repo > Actions > "Build APK" > download "TvBrowser-apk".
## Or Android Studio
Open folder, sync, Build > Build APK(s). Install: `adb connect <tv-ip>` then `adb install app-debug.apk`.

## Blocking layers
1. uBlock Origin (auto-installed from addons.mozilla.org on first launch; needs internet once)
2. Firefox tracking protection (ads, analytics, social) + Safe Browsing
3. All script/ad-opened windows denied; intent:// market:// and other non-web links denied

## Remote
D-pad = pointer, OK = click, push at top/bottom edge = scroll, MENU = toolbar, BACK = back/exit/leave fullscreen. USB/Bluetooth mouse works.

## If the build errors
GeckoView's API changes between versions. Likely spots: `onInstallPromptRequest` (maybe `onInstallPrompt`) and `contentBlocking(...)` in MainActivity.kt. Paste the error to Claude.
