# Groceries – project notes for Claude

Shared household grocery list with smart suggestions, nearby stores, and a shared recipe box.
Android (Kotlin/Compose) and iPhone (SwiftUI) apps share one Firebase project and one Vercel server,
so households, lists, and recipes are shared across platforms. Josh (product, not a full-time developer)
owns it; testers are friends and family.

## Layout

- `app/` – Android app. Package `com.grocerylist.app`, minSdk 26. Data layer in `data/`, screens in `ui/`.
- `ios/` – iPhone app, SwiftUI, iOS 26+ (Liquid Glass). Bundle ID **`com.householdgroceries.app`**
  (`com.grocerylist.app` was taken on the App Store; Josh doesn't want their name in the ID). Android keeps
  `com.grocerylist.app`. Project generated from `ios/project.yml` by XcodeGen; `Groceries.xcodeproj` isn't committed.
- `server/` – Vercel functions (TypeScript): `/api/recipe-chat`, `/api/recipe-import`, `/api/stores`, `/api/send-verification`,
  `/api/send-password-reset`, `/api/health`; `public/account.html` is the page email links open.
- `firestore.rules` – security rules. **Josh publishes these** (Firebase console → Firestore →
  Rules); after changing them, copy them to the clipboard (`LANG=en_US.UTF-8 pbcopy < firestore.rules`) and also paste the full
  text in chat.
- `release-notes/X.Y.txt` / `ios-X.Y.txt` – tester-facing notes per release, under ADDED / CHANGED / FIXED
  (empty sections left out; from 0.8 on). One file feeds Firebase, TestFlight and GitHub.

## Keep the platforms in step

Android and iOS mirror each other: same Firestore documents and field names (timestamps are device
millis as Int64/Long), same rules, same logic (suggestion ranking, kitchen quick adds, "stores in use",
ingredient matching, store brand logos, servings scaling). When changing behavior on one, change the other, or say why not.
Android files to iOS files: `Suggestions.kt`/`IngredientMatch.kt`/`StoreBrands.kt`/`Aisles.kt`/`Scaling.kt` → `ios/Groceries/Data/Logic.swift`;
repositories → `*Service.swift`; `OnboardingScreen.kt` ↔ `OnboardingView.swift`; `AccountDeletion` exists in both.

## Backend facts

- Firebase project `grocery-list-d5a77` (Auth: email, Google, Apple-on-iOS-only; Firestore). Email
  enumeration protection is on. Password reset must never reveal whether an email has an account (Josh's call);
  email confirmation is optional (a Settings row with Resend, never a sign-up blocker).
  Config files `app/google-services.json` and `ios/Groceries/App/GoogleService-Info.plist` are git-ignored.
- Firestore: `users/{uid}`, `households/{hid}` (owner = `memberIds[0]`) with `stores`, `lists/{id}/items`,
  `itemStats`, `onHand`, `savedRecipes` (the recipe box); `inviteCodes/{code}`. The old suggestion docs
  (`recipes/current`, `recipes/preferences`) are no longer read; account deletion still removes them.
- Server: https://grocery-recipes.vercel.app (Vercel project `grocery-recipes`, account jcalebf-cmyk).
  Env vars set by Josh in Vercel: `YOUTUBE_API_KEY`, `ANTHROPIC_API_KEY`, `GOOGLE_PLACES_API_KEY`.
  Recipe box (replaced AI suggestions, cuisines and the "Tonight" picker in Nov 2026): households save recipes
  three ways. `/api/recipe-chat`: a stateless chat (the app sends the whole conversation each turn and keeps
  it only while the screen is open); replies are a short message plus 0–3 recipe cards. `/api/recipe-import`:
  pasted text is sorted into ingredients and steps keeping the author's wording; a YouTube link reads the
  video's description and the creator's own comments (pinned recipes live there) through the YouTube Data API
  (2 quota units), copies the recipe if the creator posted it (`source: youtube`), else Claude writes one
  for the dish in the title (`source: ai`, labeled as possibly differing from the video). Transcripts aren't
  used: YouTube blocks them from cloud servers. All on Claude Haiku 5.5 (Josh chose it for cost).
  Endpoints require a Firebase ID token and have per-user daily limits (stores 20, chat 40, import 20) in
  Vercel Runtime Cache.
- Nearby stores: Google Places, cached on the phone 7 days per ~1.5 km area to keep costs down. Since 0.9 the apps
  send exact location (Android asks for precise; approximate still works) and re-measure saved stores' distances.
- Instacart handoff: investigated (Oct 2026) sending a list's items to Instacart as a shoppable page via
  Instacart's Developer Platform `products_link` endpoint. No OAuth needed and nothing to pin a list's store to
  (confirmed: no `retailer_key`/store field in that API), so the design would've been a plain "Send to Instacart"
  button, no onboarding step. Not built: Instacart's developer applications are closed with no waitlist
  ("check back in the future") — don't restart this without checking instacart.com/company/business/developers first.
- Auth emails (confirmation, password reset) come from the server, not Firebase: Firebase Admin makes the link,
  Resend sends it (designs in `server/lib/emailTemplates.ts`), and the link opens `householdgroceries.app/account`.
  Reset needs no sign-in and always answers ok immediately (limits: 5/day per address, 20/day per network).
  Extra env vars: `RESEND_API_KEY`, `FIREBASE_SERVICE_ACCOUNT`. `firebase-admin` is pinned to 13.x: 14.x pulls
  in an ESM-only `jose` that Vercel's runtime can't `require`.
- Domain `householdgroceries.app` (bought in Vercel, jcalebf-cmyk; DNS in Vercel): Resend records, ImprovMX
  forwards `hello@` to Josh's Gmail, DMARC `p=none`. The bare domain redirects to `www` (Vercel default).
  Homepage is `server/public/index.html` (an empty domain hurt deliverability); its screenshot `app-list.png`
  came from a temporary iOS sample-data view with a made-up store, "Corner Market" (no real chain logos on the site).
- Email deliverability (Oct 13): SPF, DKIM and DMARC all pass in Gmail; Resend click/open tracking are off
  (keep them off). Some emails landing in spam is because the domain is new; testers mark them "Not spam".
- The Vercel MCP connector is signed into a different account; use the `vercel` CLI (linked at the repo root).
- Firestore listeners the rules refuse stop silently and never retry (Android's `snapshots()` wraps the error in
  a cancellation; check `e.cause`). So the session only goes ready on the server's copy of the household
  (not this phone's unsaved one), and a denial while a join is still saving waits for the saved profile.

## How to build and ship

- **Deploys:** pushing to `main` auto-deploys the server (only when `server/` changes). Manual deploys must
  run from the **repo root** (`vercel deploy --prod --yes`), never from `server/` – the project's Root
  Directory is `server`, so deploying from inside it breaks production. `.vercelignore` keeps root deploys
  under Vercel's upload limit. Check `/api/health` afterwards. Claude's auto mode blocks running production
  deploys, so Claude ships server changes by pushing to `main` (when Josh asks) or Josh runs the deploy.
- **Android:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease testDebugUnitTest`.
  Release signing key lives outside the repo in `../GroceryList-signing/` (backed up in Josh's LastPass);
  never commit it. Josh's Pixel has the release-signed app, so install release builds on it
  (`adb -s 47221FDAS000XD install -r app/build/outputs/apk/release/app-release.apk`); debug builds won't install over it.
  `gradlew installDebug` targets every connected device, Pixel included; for the emulator use `adb -e install -r`.
- **Android releases:** use the `release-android` skill (`.claude/skills/release-android/`). Auto mode blocks
  the Firebase upload (it emails testers) unless Josh asks for that step directly ("upload and send it").
- **iPhone releases:** use the `release-ios` skill (archive → upload to TestFlight via `ios/ExportOptions.plist`).
- **iOS:** Xcode 27; `xcode-select` points at Xcode since Nov 3 (the `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`
  prefix is harmless if it's ever reset). Run `xcodegen` in `ios/` after editing
  `project.yml`. Simulator builds must be signed locally (project default) or Firebase can't use the keychain.
  Firebase iOS SDK is pinned to 12.x because 13.x conflicts with GoogleSignIn 10 (App Check versions).
  Apple team `6759US4CJM`; Josh's Mac is the team's registered development device (Apple won't issue any
  signing profile until one device exists). Xcode is signed into Josh's Apple account, so
  `-allowProvisioningUpdates` handles certificates and profiles.
  If the TestFlight export fails with "PLA Update available" / no "iOS Distribution" certificate, Josh must
  accept Apple's updated Program License Agreement at developer.apple.com/account, then rerun the export.
  Material 3 on Android is pinned to 1.5.0-beta01 for the Expressive theme. Hand-built clickable chips need
  `Modifier.minimumInteractiveComponentSize()` (48dp, like stock chips) or they crowd what's around them.
- **Checking signed-in screens without an account:** temporarily swap `RootView()` (iOS) or `GroceryApp()`
  (Android) for a debug-only view with sample data, check it, then delete it before committing.
- Firebase App Distribution always serves the APK as `app.apk`; the file can't be renamed for testers.

## Working with Josh

- Run the `wrap-up` skill at the end of a task to keep this file current; keep this file short.
- Cloud sessions push straight to `main`; `git pull` on the Mac before building a release from their work.
- Commit and push **only when asked** ("deploy" covers the Firebase/TestFlight uploads, not the release commit/tags). Before committing, check no config/key/signing files are staged.
- Ask before changing behavior Josh hasn't asked for; fix clear bugs directly.
- Verify UI changes in the simulator/emulator before calling them done; say plainly what wasn't tested.
- Josh prefers concise explanations of what changed and what Josh needs to do (Firebase/Vercel/Apple steps).
- Recipe box, Josh's calls: the kitchen is background for the chat, never a limit on what it suggests;
  pasted recipes keep the author's wording (only ingredients get structured); chats aren't saved; no cuisines.
  The servings control scales ingredient amounts only (steps keep the original amounts, and say so).
- Josh's calls: closing out a list just confirms (unchecked items stay on it); swiping left on Home closes out
  active lists and deletes past ones, each after a confirmation; items in My kitchen are never suggested on lists;
  one show/hide toggle covers both password fields.
- List suggestions: 3+ adds, last added within 60 days; long-press a chip → "Don't suggest" (`dismissedAt` on the
  `itemStats` doc) until it's added again. Kitchen quick adds and recipe regulars keep the looser rules (2+ adds, no cutoff).
- iOS filled buttons use `prominentGlassButton()` (readable text in dark mode), not `.glassProminent` directly.
  Home's swipe rows (`SwipeAction.swift`) use a UIKit pan; a SwiftUI drag let the row's tap open the list.

## Status (Nov 18, 2026)

- **In progress, uncommitted: the recipe box** (both apps + server), replacing AI recipe suggestions. Builds and
  unit tests pass; screens checked in the emulator and simulator with temporary sample data only. Not yet run
  against the real server: the chat and import prompts have never been called (no local Claude key), so the
  first real test needs the server deployed. Deploying removes `/api/recipes`, so 0.13 apps lose "New ideas".
  Ship as 0.14 on both (iOS build 13). Keep `YOUTUBE_API_KEY` in Vercel (now used for video links).
- Idea Josh raised, not built: a "Share to Groceries" target (Android share intent; iOS Share Extension or a
  Shortcuts action) so recipes from the Claude/Gemini apps open straight in the paste screen.
- **Android: 0.13.1** (code 14) on Firebase to all 3 testers (no groups yet), tagged `v0.13.1`.
- **iPhone: 0.13.1** (build 12) in TestFlight, submitted for Beta App Review, tagged `ios-v0.13.1`.
  0.13.1 = Firebase 12.19.3 (Google's urgent Firestore cert fix); Firebase must stay ≥ 12.19.3.
- Recent releases: 0.10 = edit list items, Surprise me, Pantry & seasonings tab, recipes without a kitchen,
  richer recipes. 0.11 = salt and pepper first in pantry quick adds, sheet/tab-label fixes. 0.12 = list items
  grouped by store aisle. 0.13 = stricter list suggestions and long-press "Don't suggest" (written in a cloud
  session; only unit-tested, not tried on a phone or simulator).
- Not yet tried with real accounts: "Don't suggest", joining with a code, removal on Android, kitchen items
  dropping out of list suggestions, swipes on real lists, the confirmation email.
- App name "Groceries" is set in Google Auth Platform → Branding and Firebase's public-facing name.
  Josh's call: `www` stays the primary domain, and Firebase's email template action URL stays as is
  (don't suggest changing either).
- "Clear checked items" removes checked items from the list; Josh wants it kept as is (name and trash icon).
- Optional cleanups Josh may do: delete the unused `com.grocerylist.app` iOS app in Firebase (keep the
  Android app), and remove `com.joshfranzen.groceries` from Apple Identifiers if it was registered.
