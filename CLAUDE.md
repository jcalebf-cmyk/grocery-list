# Groceries – project notes for Claude

Shared household grocery list with smart suggestions, nearby stores, and AI recipe ideas.
Android (Kotlin/Compose) and iPhone (SwiftUI) apps share one Firebase project and one Vercel server,
so households, lists, and recipes are shared across platforms. Josh (product, not a full-time developer)
owns it; testers are friends and family.

## Layout

- `app/` – Android app. Package `com.grocerylist.app`, minSdk 26. Data layer in `data/`, screens in `ui/`.
- `ios/` – iPhone app, SwiftUI, iOS 26+ (Liquid Glass). Bundle ID **`com.householdgroceries.app`**
  (`com.grocerylist.app` was taken on the App Store; Josh doesn't want their name in the ID). Android keeps
  `com.grocerylist.app`. Project generated from `ios/project.yml` by XcodeGen; `Groceries.xcodeproj` isn't committed.
- `server/` – Vercel functions (TypeScript): `/api/recipes`, `/api/stores`, `/api/health`.
- `firestore.rules` – security rules. **Josh publishes these** (Firebase console → Firestore →
  Rules); after changing them, copy them to the clipboard (`LANG=en_US.UTF-8 pbcopy < firestore.rules`) and also paste the full
  text in chat.
- `release-notes/X.Y.txt` – tester-facing notes per Android release.

## Keep the platforms in step

Android and iOS mirror each other: same Firestore documents and field names (timestamps are device
millis as Int64/Long), same rules, same logic (suggestion ranking, kitchen quick adds, "stores in use",
ingredient matching, store brand logos). When changing behavior on one, change the other, or say why not.
Android files to iOS files: `Suggestions.kt`/`IngredientMatch.kt`/`StoreBrands.kt` → `ios/Groceries/Data/Logic.swift`;
repositories → `*Service.swift`; `AccountDeletion` exists in both.

## Backend facts

- Firebase project `grocery-list-d5a77` (Auth: email, Google, Apple-on-iOS-only; Firestore). Email
  enumeration protection is on. Password reset must never reveal whether an email has an account (Josh's call);
  email confirmation is optional (a Settings row with Resend, never a sign-up blocker). Firebase hosts the email links.
  Config files `app/google-services.json` and `ios/Groceries/App/GoogleService-Info.plist` are git-ignored.
- Firestore: `users/{uid}`, `households/{hid}` (owner = `memberIds[0]`) with `stores`, `lists/{id}/items`,
  `itemStats`, `onHand`, `recipes/current`, `recipes/preferences`; `inviteCodes/{code}`.
- Server: https://grocery-recipes.vercel.app (Vercel project `grocery-recipes`, account jcalebf-cmyk).
  Env vars set by Josh in Vercel: `YOUTUBE_API_KEY`, `ANTHROPIC_API_KEY`, `GOOGLE_PLACES_API_KEY`.
  Recipes: Claude Haiku 5.5 (Josh chose it for cost) writes them, then each gets a YouTube video of a similar
  dish (~600 of 10,000 daily quota units per request; apps key recipes by `videoId`). Favorite cuisines live in
  `recipes/preferences` (missing = household hasn't done cuisine setup yet); "Tonight" picks are `mood` on
  `recipes/current` and last until cleared or the daily refresh. Cuisine list: `Cuisines.kt` ↔ `Logic.swift`.
  Endpoints require a Firebase ID token and have
  per-user daily limits (stores 20, recipes 8) in Vercel Runtime Cache.
- Nearby stores: Google Places, cached on the phone 7 days per ~1.5 km area to keep costs down.

## How to build and ship

- **Deploys:** pushing to `main` auto-deploys the server (only when `server/` changes). Manual deploys must
  run from the **repo root** (`vercel deploy --prod --yes`), never from `server/` – the project's Root
  Directory is `server`, so deploying from inside it breaks production. `.vercelignore` keeps root deploys
  under Vercel's upload limit. Check `/api/health` afterwards.
- **Android:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease testDebugUnitTest`.
  Release signing key lives outside the repo in `../GroceryList-signing/` (backed up in Josh's LastPass);
  never commit it. Josh's Pixel has the release-signed app, so install release builds on it
  (`adb -s 47221FDAS000XD install -r app/build/outputs/apk/release/app-release.apk`); debug builds won't install over it.
  `gradlew installDebug` targets every connected device, Pixel included; for the emulator use `adb -e install -r`.
- **Android releases:** use the `release-android` skill (`.claude/skills/release-android/`).
- **iPhone releases:** use the `release-ios` skill (archive → upload to TestFlight via `ios/ExportOptions.plist`).
- **iOS:** Xcode 27, but `xcode-select` still points at Command Line Tools, so prefix commands with
  `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`. Run `xcodegen` in `ios/` after editing
  `project.yml`. Simulator builds must be signed locally (project default) or Firebase can't use the keychain.
  Firebase iOS SDK is pinned to 12.x because 13.x conflicts with GoogleSignIn 10 (App Check versions).
  Apple team `6759US4CJM`; Josh's Mac is the team's registered development device (Apple won't issue any
  signing profile until one device exists). Xcode is signed into Josh's Apple account, so
  `-allowProvisioningUpdates` handles certificates and profiles.
  Material 3 on Android is pinned to 1.5.0-beta01 for the Expressive theme.

## Working with Josh

- Run the `wrap-up` skill at the end of a task to keep this file current; keep this file short.
- Commit and push **only when asked**. Before committing, check no config/key/signing files are staged.
- Ask before changing behavior Josh hasn't asked for; fix clear bugs directly.
- Verify UI changes in the simulator/emulator before calling them done; say plainly what wasn't tested.
- Josh prefers concise explanations of what changed and what Josh needs to do (Firebase/Vercel/Apple steps).

## Status (Oct 12, 2026)

- **Android:** 0.4 (code 4) is the latest release, on Firebase App Distribution and tagged `v0.4`
  (no tester groups yet; it goes to every tester individually).
- **iPhone:** 0.4 (build 3) is in TestFlight (internal group, automatic distribution on), tagged `ios-v0.4`.
  Version numbers match across platforms (Josh's call). The next upload must be build 4.
- 0.4 = "Forgot password?", optional email confirmation, stricter ingredient matching. The confirmation
  email and Settings row haven't been checked with a real account yet.
- TestFlight "What to Test" is now set by `release-ios` through the App Store Connect API (key in
  `../GroceryList-signing/`). Reading works; the write step gets its first real run on build 4.
- "Clear checked items" removes checked items from the list; Josh wants it kept as is (name and trash icon).
- Optional cleanups Josh may do: delete the unused `com.grocerylist.app` iOS app in Firebase (keep the
  Android app), and remove `com.joshfranzen.groceries` from Apple Identifiers if it was registered.
