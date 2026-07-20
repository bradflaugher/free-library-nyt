# Free Library NYTimes Launcher

A tiny Android 16+ app for managing New York Times library access through the
Free Library of Philadelphia.

The app opens the NYTimes account page in a WebView with persistent login
cookies. Its top bar has manual controls to load the Free Library activation
page, refresh the current page, or open the installed NYTimes app. If NYTimes is
not installed, its Google Play listing opens instead. The app does not inspect
or infer subscription status.

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

[Download the latest APK](https://github.com/bradflaugher/free-library-nyt/releases/latest/download/library-nytimes.apk)
