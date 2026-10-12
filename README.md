# Grocery List

An Android grocery list app for households, plus a small server that suggests recipes.

- **`app/`** – Android app (Kotlin, Jetpack Compose). Sign-in, households, per-store lists, and item suggestions run on Firebase Auth and Firestore.
- **`server/`** – Vercel function where Claude Haiku 5.5 writes simple recipe cards from a household's regular and on-hand items, then attaches a YouTube video of a similar dish to each. Deploys automatically from `main`.
- **`ios/`** – iPhone app (SwiftUI, iOS 26+, Liquid Glass) on the same Firebase project and server.
- **`firestore.rules`** – Firestore security rules. Paste into Firebase console → Firestore Database → Rules after changing them.

## Setup on a new machine

1. Download `google-services.json` from Firebase (Project settings → Your apps → `com.grocerylist.app`) into `app/`. It's not committed.
2. Open the project in Android Studio and press Run.

## iPhone app

The Xcode project is generated from `ios/project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) and isn't committed. To build:

1. `brew install xcodegen`, then run `xcodegen` in `ios/`.
2. Download `GoogleService-Info.plist` from Firebase (Project settings → Your apps → the iOS app) into `ios/Groceries/App/`. It's not committed.
3. Open `ios/Groceries.xcodeproj` in Xcode and run.

## Release builds

Release APKs are signed with a key kept outside the repo, in `../GroceryList-signing/` (`grocerylist-release.jks` plus `keystore.properties`). Back that folder up; every update must be signed with the same key. Build with `./gradlew assembleRelease`; the APK lands in `app/build/outputs/apk/release/`.

The server needs `YOUTUBE_API_KEY` and `ANTHROPIC_API_KEY` set as environment variables on the Vercel project.
