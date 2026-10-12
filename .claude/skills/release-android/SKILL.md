---
name: release-android
description: Build and prepare a new Android release of the Groceries app for Firebase App Distribution. Use when Josh asks for a new Android version, release, or build for testers.
---

# Android release

Run from the repo root. Use `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` for Gradle.

1. **Find what changed** since the last release: `git describe --tags --abbrev=0` for the last tag, then
   `git log --oneline <tag>..HEAD -- app firestore.rules`. If nothing app-facing changed, say so before building.

2. **Bump the version** in `app/build.gradle.kts`: `versionCode` +1, and `versionName` to the next
   `0.X` unless Josh names one.

3. **Build and verify:** `./gradlew assembleRelease testDebugUnitTest`. Confirm all tests pass, then check the APK:
   - `aapt2 dump badging` shows the new versionCode/versionName
   - `apksigner verify --print-certs` shows SHA-1 `f20085a9b0015d47b3bdb433f12c769fa4054168` (the release key).
     If release signing is missing, `../GroceryList-signing/` isn't present; stop and tell Josh.

4. **Stage the files for Josh:**
   - Copy the APK to `../GroceryList-releases/Groceries-X.Y.apk`.
   - Write `release-notes/X.Y.txt` for testers: plain text (App Tester doesn't render Markdown), "WHAT'S NEW"
     bullets in user terms, no implementation details, ending with
     "Found a bug or have an idea? Let Josh know – a screenshot helps a lot."
   - Copy the notes to `../GroceryList-releases/release-notes-X.Y.txt` and to the clipboard (`pbcopy`).
   - Reveal the APK in Finder (`open -R`).

5. **Optionally install on Josh's Pixel** if it's connected: `adb -s 47221FDAS000XD install -r` the release APK
   (it has the release-signed app, so debug builds won't install over it).

6. **Show Josh the notes and ask to ship.** Shipping notifies testers and publishes a GitHub release, so wait
   for a clear yes. One yes covers steps 7 and 8.

7. **Upload to Firebase App Distribution** with the Firebase CLI:
   ```
   firebase appdistribution:distribute ../GroceryList-releases/Groceries-X.Y.apk \
     --app 1:566834645408:android:e9a111d798cb0d69107cc5 --project grocery-list-d5a77 \
     --release-notes-file release-notes/X.Y.txt --testers <emails>
   ```
   Send it to every current tester: get their emails from `firebase appdistribution:testers:list --project
   grocery-list-d5a77` (comma-separated, and never write them into the repo, which is public). If Josh has since
   made tester groups (`firebase appdistribution:groups:list`), use `--groups <alias>` instead and ask which one if
   there are several. If `firebase` isn't installed, install it (`npm install -g firebase-tools`); if it isn't
   signed in, ask Josh to run `firebase login` in a terminal (it opens a browser for his Google account), then retry. If the upload can't happen, fall back to the console: App Distribution → upload APK → paste
   notes → add testers → Distribute.

8. **Tag the release on GitHub:** commit just `app/build.gradle.kts` and `release-notes/X.Y.txt`
   ("Release Android X.Y"), push, confirm `git diff --quiet HEAD -- app server` (the tag matches the APK), create
   an annotated tag `vX.Y`, push it, then `gh release create vX.Y --verify-tag --title "Groceries X.Y"
   --notes-file release-notes/X.Y.txt --prerelease`. Never attach the APK (the repo is public).

9. **Tell Josh** what shipped: the Firebase release (testers get an email), the GitHub release link, and anything
   that didn't happen.
