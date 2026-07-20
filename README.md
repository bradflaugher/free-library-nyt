# Free Library NYTimes Launcher

A tiny Android 16+ app for checking and activating a New York Times access pass
through the Free Library of Philadelphia.

The app loads the NYTimes subscription page in a WebView with persistent login
cookies. It uses the account page's authenticated account request to check for a
current `NEWS` product specifically, so a Wirecutter-only subscription does not
count as News access. An account with News access goes directly to the installed
NYTimes app. Otherwise, the app goes to the library registration page in the
same WebView; once NYTimes reports active News access, the app opens NYTimes. If
the NYTimes app is not installed, its Google Play listing opens instead.

The app does not read credentials, form fields, cookies, or general page text,
and it does not expose a JavaScript-to-Android bridge.

## Build

Install Android SDK Platform 36, then run:

```shell
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Releases

Every push to `main` builds a signed APK with a UTC date-based version such as
`2026.07.20.12`. GitHub Actions replaces the repository's single `latest`
release, so the Releases page always contains only the newest APK.
