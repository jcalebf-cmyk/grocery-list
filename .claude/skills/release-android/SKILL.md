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

6. **Tell Josh** where the APK is, show the notes, and remind Josh of the upload steps: Firebase console →
   App Distribution → upload APK → paste notes → add testers → Distribute.

7. **Only when Josh asks:** commit just `app/build.gradle.kts` and `release-notes/X.Y.txt` ("Release Android X.Y"),
   push, confirm `git diff --quiet HEAD -- app server` (the tag matches the APK), create an annotated tag
   `vX.Y`, push it, then `gh release create vX.Y --verify-tag --title "Groceries X.Y" --notes-file
   release-notes/X.Y.txt --prerelease`. Never attach the APK (the repo is public).
