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
- `server/` – Vercel functions (TypeScript): `/api/recipes`, `/api/stores`, `/api/send-verification`,
  `/api/send-password-reset`, `/api/health`; `public/account.html` is the page email links open.
- `firestore.rules` – security rules. **Josh publishes these** (Firebase console → Firestore →
  Rules); after changing them, copy them to the clipboard (`LANG=en_US.UTF-8 pbcopy < firestore.rules`) and also paste the full
  text in chat.
- `release-notes/X.Y.txt` / `ios-X.Y.txt` – tester-facing notes per release, under ADDED / CHANGED / FIXED
  (empty sections left out; from 0.8 on). One file feeds Firebase, TestFlight and GitHub.

## Keep the platforms in step

Android and iOS mirror each other: same Firestore documents and field names (timestamps are device
millis as Int64/Long), same rules, same logic (suggestion ranking, kitchen quick adds, "stores in use",
ingredient matching, store brand logos). When changing behavior on one, change the other, or say why not.
Android files to iOS files: `Suggestions.kt`/`IngredientMatch.kt`/`StoreBrands.kt`/`Aisles.kt` → `ios/Groceries/Data/Logic.swift`;
repositories → `*Service.swift`; `OnboardingScreen.kt` ↔ `OnboardingView.swift`; `AccountDeletion` exists in both.

## Backend facts

- Firebase project `grocery-list-d5a77` (Auth: email, Google, Apple-on-iOS-only; Firestore). Email
  enumeration protection is on. Password reset must never reveal whether an email has an account (Josh's call);
  email confirmation is optional (a Settings row with Resend, never a sign-up blocker).
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
- Auth emails (confirmation, password reset) come from the server, not Firebase: Firebase Admin makes the link,
  Resend sends it (designs in `server/lib/emailTemplates.ts`), and the link opens `householdgroceries.app/account`.
  Reset needs no sign-in and always answers ok immediately (limits: 5/day per address, 20/day per network).
  Extra env vars: `RESEND_API_KEY`, `FIREBASE_SERVICE_ACCOUNT`. `firebase-admin` is pinned to 13.x: 14.x pulls
  in an ESM-only `jose` that Vercel's runtime can't `require`.
- Domain `householdgroceries.app` (bought in Vercel, jcalebf-cmyk; DNS in Vercel): Resend records, ImprovMX
  forwards `hello@` to Josh's Gmail, DMARC `p=none`. The bare domain redirects to `www` (Vercel default).
- The Vercel MCP connector is signed into a different account; use the `vercel` CLI (linked at the repo root).

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
- **Checking signed-in screens without an account:** temporarily swap `RootView()` (iOS) or `GroceryApp()`
  (Android) for a debug-only view with sample data, check it, then delete it before committing.
- Firebase App Distribution always serves the APK as `app.apk`; the file can't be renamed for testers.

## Working with Josh

- Run the `wrap-up` skill at the end of a task to keep this file current; keep this file short.
- Commit and push **only when asked**. Before committing, check no config/key/signing files are staged.
- Ask before changing behavior Josh hasn't asked for; fix clear bugs directly.
- Verify UI changes in the simulator/emulator before calling them done; say plainly what wasn't tested.
- Josh prefers concise explanations of what changed and what Josh needs to do (Firebase/Vercel/Apple steps).
- Josh's calls: closing out a list just confirms (unchecked items stay on it); swiping left on Home closes out
  active lists and deletes past ones, each after a confirmation; items in My kitchen are never suggested on lists;
  one show/hide toggle covers both password fields.
- iOS filled buttons use `prominentGlassButton()` (readable text in dark mode), not `.glassProminent` directly.
  Home's swipe rows (`SwipeAction.swift`) use a UIKit pan; a SwiftUI drag let the row's tap open the list.

## Status (Oct 12, 2026)

- **Android:** 0.6 (code 6) is the latest release, on Firebase App Distribution and tagged `v0.6`
  (no tester groups yet; it goes to every tester individually).
- **iPhone:** 0.6 (build 5) is in TestFlight (internal group, automatic distribution on), tagged `ios-v0.6`;
  `release-ios` sets "What to Test" and sends each build to external groups + Beta App Review automatically. Version numbers match across platforms (Josh's call).
  The next upload must be build 6.
- 0.5 = branded emails and link page, simpler close-out, swipe to close out. 0.6 = onboarding walkthrough,
  kitchen items skipped in suggestions, version in Settings, readable iOS buttons.
- Not yet tried with a real account: the onboarding flow, kitchen items dropping out of list suggestions,
  swipes on real lists, and the confirmation email sent from the app.
- Waiting on Josh: set the app name to "Groceries" in Google Auth Platform → Branding (Google sign-in still
  shows "project-566834645408") and Firebase → Project settings → Public-facing name. Optional: make the bare
  domain primary instead of `www`; point Firebase's email templates' action URL at `householdgroceries.app/account`
  so emails sent by older app versions open the new page.
- "Clear checked items" removes checked items from the list; Josh wants it kept as is (name and trash icon).
- Optional cleanups Josh may do: delete the unused `com.grocerylist.app` iOS app in Firebase (keep the
  Android app), and remove `com.joshfranzen.groceries` from Apple Identifiers if it was registered.
