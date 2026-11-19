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
  Share extension `GroceriesShare` (`ios/ShareExtension/`, bundle `com.householdgroceries.app.share`) shares the
  Firebase sign-in through keychain group `6759US4CJM.com.householdgroceries.shared` (`ios/Shared/SharedSignIn.swift`,
  both targets' entitlements); the app moves an existing sign-in into it on launch.
- `server/` – Vercel functions (TypeScript): `/api/recipe-chat`, `/api/recipe-import`, `/api/stores`, `/api/send-verification`,
  `/api/send-password-reset`, `/api/health`; `public/account.html` is the page email links open.
- `firestore.rules` – security rules. **Josh publishes these** (Firebase console → Firestore →
  Rules); after changing them, copy them to the clipboard (`LANG=en_US.UTF-8 pbcopy < firestore.rules`) and also paste the full
  text in chat.
- `release-notes/X.Y.txt` / `ios-X.Y.txt` – tester-facing notes per release, under ADDED / CHANGED / FIXED
  (empty sections left out; from 0.8 on). One file feeds Firebase, TestFlight and GitHub.

## Keep the platforms in step

Android and iOS mirror each other: same Firestore documents and field names (timestamps are device
millis as Int64/Long), same rules, same logic (suggestion ranking, "stores in use",
store brand logos, servings scaling, recipe search). When changing behavior on one, change the other, or say why not.
Android files to iOS files: `Suggestions.kt`/`StoreBrands.kt`/`Aisles.kt`/`Scaling.kt` → `ios/Groceries/Data/Logic.swift`;
repositories → `*Service.swift`; `OnboardingScreen.kt` ↔ `OnboardingView.swift`; `AccountDeletion` exists in both.

## Backend facts

- Firebase project `grocery-list-d5a77` (Auth: email, Google, Apple-on-iOS-only; Firestore). Email
  enumeration protection is on. Password reset must never reveal whether an email has an account (Josh's call);
  email confirmation is optional (a Settings row with Resend, never a sign-up blocker).
  Config files `app/google-services.json` and `ios/Groceries/App/GoogleService-Info.plist` are git-ignored.
- Firestore: `users/{uid}`, `households/{hid}` (owner = `memberIds[0]`) with `stores`, `lists/{id}/items`,
  `itemStats`, `savedRecipes` (the recipe box); `inviteCodes/{code}`. No longer read, but account deletion
  still removes them: `onHand` (My kitchen, removed in 0.14) and the old suggestions (`recipes/current`, `/preferences`).
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
  "Share to Groceries": Android's share target opens the paste screen with the text (review, then save); the
  iPhone share extension can't open the app, so it sends `save: true` and the server writes the recipe to the
  user's household with Firebase Admin (checking membership itself, since Admin skips the rules).
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
- Commit and push **only when asked**, except release commits and tags: the release skills always commit, tag and
  publish the GitHub release after a successful upload (Josh's call). Before committing, check no config/key/signing files are staged.
- Ask before changing behavior Josh hasn't asked for; fix clear bugs directly.
- Verify UI changes in the simulator/emulator before calling them done; say plainly what wasn't tested.
- Josh prefers concise explanations of what changed and what Josh needs to do (Firebase/Vercel/Apple steps).
- Recipe box, Josh's calls: pasted recipes keep the author's wording (only ingredients get structured); chats
  aren't saved; no cuisines; no My kitchen (Josh removed it: nobody keeps it up to date, and you tick only what
  you need before "Add to list"). The servings control scales ingredient amounts only (steps keep the original
  amounts, and say so). Ingredients start unticked (Josh's call); "Add to list" sits by the Ingredients heading, never pinned
  to the screen (Josh found it nagging).
- Home, Josh's calls: Shopping lists first (always a header; a "Start a list" card replaces the floating New list
  button when none is going), then a "Past lists" row opening the history, then Recipes.
- Household setup, Josh's call: ask start-or-join first (Join card above Start), never both forms on one screen.
- Josh's calls: closing out a list just confirms (unchecked items stay on it); swiping left on Home closes out
  active lists and deletes past ones (in Past lists), each after a confirmation;
  one show/hide toggle covers both password fields.
- List suggestions: 3+ adds, last added within 60 days; long-press a chip → "Don't suggest" (`dismissedAt` on the
  `itemStats` doc) until it's added again.
- iOS filled buttons use `prominentGlassButton()` (readable text in dark mode), not `.glassProminent` directly.
  Home's swipe rows (`SwipeAction.swift`) use a UIKit pan; a SwiftUI drag let the row's tap open the list.

## Status (Nov 19, 2026)

- **Android: 0.15** (code 19) on Firebase to all 3 testers, tagged `v0.15`. **iPhone: 0.15** (build 15)
  uploaded to TestFlight and submitted for Beta App Review, tagged `ios-v0.15` (0.14 build 14 was still
  waiting for review). Next iOS upload must be build 16. Firebase iOS must stay ≥ 12.19.3 (Google's Firestore cert fix).
- 0.15 = household setup asks "Is someone in your household already using Groceries?" first (Join / Start
  cards), then one screen with one field; the name is prefilled "<Last name> household". Checked with sample
  data in the emulator and simulator only; creating/joining wasn't run with a real account.
- 0.14 = recipe box (AI chat, paste text or YouTube link, Share to Groceries, servings scaling), lists-first Home
  with Past lists in its own screen, no My kitchen. Never run end to end with a real account: the chat and import
  prompts (no local Claude key), the iPhone share extension and the sign-in move into the shared keychain group
  (check a tester stays signed in after updating). The server deploy removed `/api/recipes`, so 0.13 apps lose
  "New ideas". Keep `YOUTUBE_API_KEY` in Vercel (video links).
- Recent releases: 0.12 = list items grouped by store aisle. 0.13 = stricter list suggestions and long-press "Don't suggest" (written in a cloud
  session; only unit-tested, not tried on a phone or simulator).
- Not yet tried with real accounts: "Don't suggest", joining with a code, removal on Android, swipes on real
  lists, the confirmation email.
- App name "Groceries" is set in Google Auth Platform → Branding and Firebase's public-facing name.
  Josh's call: `www` stays the primary domain, and Firebase's email template action URL stays as is
  (don't suggest changing either).
- "Clear checked items" removes checked items from the list; Josh wants it kept as is (name and trash icon).
- Optional cleanups Josh may do: delete the unused `com.grocerylist.app` iOS app in Firebase (keep the
  Android app), and remove `com.joshfranzen.groceries` from Apple Identifiers if it was registered.
