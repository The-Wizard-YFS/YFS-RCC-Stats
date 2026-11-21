# Dong Country Ledger 5000 — Android

A screen-for-screen Android copy of the iOS app in `../StickballStats`, written in Kotlin with Jetpack Compose.
The iOS app is untouched. Both apps read and write the **same Firebase project and the same Firestore
collections and fields**, so stats entered on an iPhone show up on Android and vice versa.

| | iOS (`StickballStats/`) | Android (`android/`) |
|---|---|---|
| UI | SwiftUI | Jetpack Compose |
| Data | Firestore (`players`, `weeklyStats`, `gameWeekInfo`, `careerAdjustments`, Test Mode `test_*`) | same |
| Art & fonts | `Assets.xcassets/StickballRich`, `Fonts/` | same files, copied to `app/src/main/res` |
| App ID / version | `com.stickball.stats`, 2.1 (1) | `com.stickball.stats`, 2.1 (versionCode 1) |
| Admin PIN, schedule, history, aliases, badges | Swift models | ported 1:1 in `app/src/main/java/com/stickball/stats/model` |

## 1. Connect Firebase (one time, ~5 minutes)

The Android app needs its own entry in the existing Firebase project (`dong-country-ledger-5000`):

1. Open the [Firebase console](https://console.firebase.google.com) → project **dong-country-ledger-5000**.
2. Project settings → **Your apps** → **Add app** → Android.
3. Package name: `com.stickball.stats` (nickname anything, SHA-1 not needed).
4. Download **`google-services.json`** and put it at `android/app/google-services.json`.
5. Skip the console's "add SDK" steps — the project is already set up.

Without that file the app still builds, but it shows a "Firebase not set up" screen.
`google-services.json` only identifies the project (like the iOS `GoogleService-Info.plist`), so it's fine to commit.

## 2. Run it

1. Install [Android Studio](https://developer.android.com/studio) (it includes the JDK and Android SDK).
2. **File → Open…** and pick this `android` folder. Let Gradle sync.
3. Pick an emulator or a phone with USB debugging on, and press ▶︎ Run.

From a terminal: `./gradlew installDebug` (needs `ANDROID_HOME` or `local.properties` pointing at the SDK).

**Test Mode** works like iOS: it's only in debug builds (what Android Studio runs), under Players → admin.

## 3. Release to Google Play

1. Create an upload key once (keep it safe — you need it for every update):
   ```
   keytool -genkey -v -keystore upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Create `android/keystore.properties` (ignored by git):
   ```
   storeFile=upload-key.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
   (`storeFile` is relative to the `android` folder.)
3. Build the bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. In the [Play Console](https://play.google.com/console), create the app, upload the `.aab`, and fill in the
   listing. `play_store_icon_512.png` is the store icon (made from the iOS app icon).
5. For each update, bump `versionCode` (and `versionName` to match iOS) in `app/build.gradle.kts`.

## Keeping the two apps in step

When the iOS app changes, make the same change here. The Swift → Kotlin map:

| iOS | Android |
|---|---|
| `Models/HistoricalStats.swift` (aliases + past seasons) | `model/HistoricalData.kt` (generated from the Swift file) and `model/CareerStats.kt` |
| `Models/Achievements.swift` | `model/Achievements.kt` |
| `Models/SeasonSchedule.swift` | `model/SeasonSchedule.kt` |
| `Models/Player.swift`, `WeeklyStats.swift`, `GameWeekInfo.swift` | `model/` files of the same names |
| `Services/StatsService.swift`, `AdminService.swift` | `service/` files of the same names |
| `Theme/StickballRichTheme.swift`, `TronTheme.swift` | `ui/Theme.kt`, `ui/Components.kt` |
| `ContentView.swift`, `SplashView.swift` | `MainActivity.kt`, `ui/SplashScreen.kt` |
| `Views/LeaderboardView.swift` | `ui/LeaderboardScreen.kt` |
| `Views/WeeklyStatsView.swift` | `ui/WeeklyStatsScreen.kt` |
| `Views/PastSeasonsView.swift` | `ui/PastSeasonsScreen.kt` |
| `Views/PlayersView.swift` | `ui/PlayersScreen.kt` |
| `Views/AddEditPlayerView.swift` | `ui/AddEditPlayerScreen.kt` |
| `Views/CareerStatsView.swift` (profile) | `ui/CareerDetailScreen.kt` |
| `Views/PlayerStatsEntryView.swift`, `BatchStatsEntryView.swift` | `ui/StatEntryScreens.kt` |
| `Views/WeeklyPlayerSelectionView.swift` | `ui/WeeklyPlayerSelectionScreen.kt` |

New art: add the PNG to `app/src/main/res/drawable-nodpi/` using the iOS asset name in lowercase
(`SR_New_Thing` → `sr_new_thing.png`); code refers to it by the iOS name.

## Tests

`./gradlew testDebugUnitTest` runs on the JVM (no emulator):

- `LeagueLogicTest` checks the schedule, aliases, career totals and awards against the Swift data.
- `ScreenshotTest` renders every screen with sample data (written through the app's own Firestore code into
  an offline local cache, never the real database) to `app/build/screenshots/`.

## Differences from iOS you may notice

- SF Symbols (field icons, a few fallback badge icons) are replaced by the closest Material icons.
- Sheets slide up as Android bottom sheets; the back button / swipe down closes them.
- Haptics on the splash use the phone's vibration motor, so they feel a little different per device.
