# Groceries

A shared grocery list and recipe box for households, for iPhone and Android, plus a small server behind its
recipe box. More at [householdgroceries.app](https://www.householdgroceries.app).

> **Source available, not open source.** This code is published to read. All rights are reserved: you may
> not build, run, copy or distribute it, or publish it in an app store, without written permission. See
> [LICENSE](LICENSE).

- **`app/`** – Android app (Kotlin, Jetpack Compose). Sign-in, households, per-store lists, and item suggestions run on Firebase Auth and Firestore.
- **`server/`** – Vercel functions for the recipe box: `/api/recipe-chat` (Claude Haiku 5.5 suggests recipes in a chat) and `/api/recipe-import` (sorts a pasted recipe into ingredients and steps, or reads a YouTube video's description and the creator's comments). Deploys automatically from `main`.
- **`ios/`** – iPhone app (SwiftUI, iOS 26+, Liquid Glass) on the same Firebase project and server.
- **`firestore.rules`** – Firestore security rules. Paste into Firebase console → Firestore Database → Rules after changing them.

## Maintainer setup

These notes are for the maintainer's own machines; the config files and keys they mention aren't in this repo.

### Android app

1. Download `google-services.json` from Firebase (Project settings → Your apps → `com.grocerylist.app`) into `app/`. It's not committed.
2. Open the project in Android Studio and press Run.

### iPhone app

The Xcode project is generated from `ios/project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) and isn't committed. To build:

1. `brew install xcodegen`, then run `xcodegen` in `ios/`.
2. Download `GoogleService-Info.plist` from Firebase (Project settings → Your apps → the iOS app) into `ios/Groceries/App/`. It's not committed.
3. Open `ios/Groceries.xcodeproj` in Xcode and run.

### Release builds

Release APKs are signed with a key kept outside the repo, in `../GroceryList-signing/` (`grocerylist-release.jks` plus `keystore.properties`). Back that folder up; every update must be signed with the same key. Build with `./gradlew assembleRelease`; the APK lands in `app/build/outputs/apk/release/`.

The server needs `YOUTUBE_API_KEY` and `ANTHROPIC_API_KEY` set as environment variables on the Vercel project.
