# Groceries – project notes for Claude

Shared household grocery list with smart suggestions, nearby stores, and YouTube recipe ideas.
Android (Kotlin/Compose) and iPhone (SwiftUI) apps share one Firebase project and one Vercel server,
so households, lists, and recipes are shared across platforms. Josh (product, not a full-time developer)
owns it; testers are friends and family.

## Layout

- `app/` – Android app. Package `com.grocerylist.app`, minSdk 26. Data layer in `data/`, screens in `ui/`.
- `ios/` – iPhone app, SwiftUI, iOS 26+ (Liquid Glass). Bundle ID `com.grocerylist.app`. Project generated
  from `ios/project.yml` by XcodeGen; `Groceries.xcodeproj` is not committed.
- `server/` – Vercel functions (TypeScript): `/api/recipes`, `/api/stores`, `/api/health`.
- `firestore.rules` – security rules. **Josh publishes these** (Firebase console → Firestore →
  Rules); after changing them, copy them to the clipboard (`pbcopy < firestore.rules`) and also paste the full
  text in chat.
- `release-notes/X.Y.txt` – tester-facing notes per Android release.

## Keep the platforms in step

Android and iOS mirror each other: same Firestore documents and field names (timestamps are device
millis as Int64/Long), same rules, same logic (suggestion ranking, kitchen quick adds, "stores in use",
ingredient matching, store brand logos). When changing behavior on one, change the other, or say why not.
Android files to iOS files: `Suggestions.kt`/`IngredientMatch.kt`/`StoreBrands.kt` → `ios/Groceries/Data/Logic.swift`;
repositories → `*Service.swift`; `AccountDeletion` exists in both.

## Backend facts

- Firebase project `grocery-list-d5a77` (Auth: email, Google, Apple-on-iOS-only; Firestore).
  Config files `app/google-services.json` and `ios/Groceries/App/GoogleService-Info.plist` are git-ignored.
- Firestore: `users/{uid}`, `households/{hid}` (owner = `memberIds[0]`) with `stores`, `lists/{id}/items`,
  `itemStats`, `onHand`, `recipes/current`; `inviteCodes/{code}`.
- Server: https://grocery-recipes.vercel.app (Vercel project `grocery-recipes`, account jcalebf-cmyk).
  Env vars set by Josh in Vercel: `YOUTUBE_API_KEY`, `ANTHROPIC_API_KEY`, `GOOGLE_PLACES_API_KEY`.
  Recipes use Claude Haiku 5.5 (Josh chose it for cost). Endpoints require a Firebase ID token and have
  per-user daily limits (stores 20, recipes 8) in Vercel Runtime Cache.
- Nearby stores: Google Places, cached on the phone 7 days per ~1.5 km area to keep costs down.

## How to build and ship

- **Deploys:** pushing to `main` auto-deploys the server (only when `server/` changes). Manual deploys must
  run from the **repo root** (`vercel deploy --prod --yes`), never from `server/` – the project's Root
  Directory is `server`, so deploying from inside it breaks production. Check `/api/health` afterwards.
- **Android:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease testDebugUnitTest`.
  Release signing key lives outside the repo in `../GroceryList-signing/` (backed up in Josh's LastPass);
  never commit it. Josh's Pixel has the release-signed app, so install release builds on it
  (`adb -s 47221FDAS000XD install -r app/build/outputs/apk/release/app-release.apk`); debug builds won't install over it.
- **Android releases:** use the `release-android` skill (`.claude/skills/release-android/`).
- **iOS:** Xcode 27, but `xcode-select` still points at Command Line Tools, so prefix commands with
  `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`. Run `xcodegen` in `ios/` after editing
  `project.yml`. Simulator builds must be signed locally (project default) or Firebase can't use the keychain.
  Firebase iOS SDK is pinned to 12.x because 13.x conflicts with GoogleSignIn 10 (App Check versions).
  Material 3 on Android is pinned to 1.5.0-beta01 for the Expressive theme.

## Working with Josh

- Run the `wrap-up` skill at the end of a task to keep this file current; keep this file short.

- Commit and push **only when asked**. Before committing, check no config/key/signing files are staged.
- Ask before changing behavior Josh hasn't asked for; fix clear bugs directly.
- Verify UI changes in the simulator/emulator before calling them done; say plainly what wasn't tested.
- Josh prefers concise explanations of what changed and what Josh needs to do (Firebase/Vercel/Apple steps).

## Status (Oct 2026)

- Android 0.2 released to testers (tags v0.1, v0.2). iPhone app working in the simulator.
- Waiting on Josh's Apple Developer Program approval, then: set `DEVELOPMENT_TEAM` in `ios/project.yml`,
  test Sign in with Apple, archive, upload to App Store Connect, and set up TestFlight internal testing.
- Open question for Josh: ingredient matching marks "Rice vinegar" as in the kitchen when "Rice" is
  (both platforms); Josh hasn't decided whether to make it stricter.
- "Clear checked items" removes checked items from the list; Josh wants it kept as is (name and trash icon).
