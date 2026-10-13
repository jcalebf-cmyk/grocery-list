---
name: release-ios
description: Build and upload a new iPhone build of the Groceries app to TestFlight. Use when Josh asks for a new iOS/iPhone version, build, or TestFlight upload.
---

# iPhone (TestFlight) release

Run from `ios/`. Prefix every Xcode command with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`
(`xcode-select` still points at Command Line Tools).

1. **Find what changed** since the last iOS release: `git describe --tags --match 'ios-v*' --abbrev=0`
   (if there's no iOS tag yet, use the commit that added the app), then `git log --oneline <ref>..HEAD -- ios firestore.rules`.

2. **Bump the version** in `ios/project.yml`:
   - `CURRENT_PROJECT_VERSION` +1 **every upload** – App Store Connect rejects a repeated build number.
   - `MARKETING_VERSION` to the next `0.X` for a new release (keep it for a rebuild of the same release).
   Then run `xcodegen`.

3. **Archive:** `xcodebuild -project Groceries.xcodeproj -scheme Groceries -configuration Release
   -destination 'generic/platform=iOS' -archivePath build/Groceries.xcarchive -allowProvisioningUpdates archive`.
   If it fails with "no devices", the registered Mac was removed: rebuild once for
   `-destination "platform=macOS,arch=arm64,variant=Designed for iPad,id=<Mac id from -showdestinations>"`
   with `-allowProvisioningUpdates -allowProvisioningDeviceRegistration`, then archive again.

4. **Verify the archive** (`build/Groceries.xcarchive/Products/Applications/Groceries.app`):
   - `CFBundleIdentifier` is `com.householdgroceries.app`, and the versions match step 2.
   - `codesign -d --entitlements - --xml` includes `com.apple.developer.applesignin` and team `6759US4CJM`.
   - The bundled `GoogleService-Info.plist` has `BUNDLE_ID` `com.householdgroceries.app`.

5. **Upload:** `xcodebuild -exportArchive -archivePath build/Groceries.xcarchive -exportOptionsPlist
   ExportOptions.plist -exportPath build/export -allowProvisioningUpdates`. Look for "Upload succeeded".
   "Upload Symbols Failed" warnings for Firebase's prebuilt frameworks (grpc, absl, openssl_grpc,
   FirebaseFirestoreInternal) are expected and harmless.

6. **Write tester notes** in `release-notes/ios-X.Y.txt` (same style as the Android notes: plain text,
   user-facing, ending with "Found a bug or have an idea? Let Josh know – a screenshot helps a lot.").

7. **Finish in TestFlight** (Josh wants this automatic, not pasted): from the repo root, run in the background
   `node .claude/skills/release-ios/testflight.mjs X.Y N release-notes/ios-X.Y.txt`. Using the key in
   `../GroceryList-signing/app-store-connect.json`, it waits for Apple to finish processing (about 5–30 minutes),
   sets **What to Test**, then adds the build to every external group and submits it for Beta App Review.
   `--dry-run` only reports the build's state, its text, its external groups and its review status;
   `--internal-only` skips the external groups and review. If setting the text fails, copy the notes
   (`LANG=en_US.UTF-8 pbcopy`) and have Josh paste them into App Store Connect → TestFlight → the build →
   **What to Test**, then rerun the script (it skips what's already done).
   Internal testers get the build by automatic distribution once processing ends; external testers only
   get it after review approves it (usually under a day for a new version, often minutes for a later build
   of an approved version). Review needs App Store Connect's **Test Information** (contact, sign-in account)
   filled in; if the submission is rejected for missing info, tell Josh what to fill in.

8. **Only when Josh asks:** commit `ios/project.yml` and the notes ("Release iOS X.Y (N)"), push, tag
   `ios-vX.Y`, and create a GitHub pre-release with the notes and no binaries.
