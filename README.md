# Grocery List

An Android grocery list app for households, plus a small server that suggests recipes.

- **`app/`** – Android app (Kotlin, Jetpack Compose). Sign-in, households, per-store lists, and item suggestions run on Firebase Auth and Firestore.
- **`server/`** – Vercel function that searches YouTube for easy recipes using a household's regular items and has Claude Haiku 5.5 turn them into simple recipe cards. Deploys automatically from `main`.
- **`firestore.rules`** – Firestore security rules. Paste into Firebase console → Firestore Database → Rules after changing them.

## Setup on a new machine

1. Download `google-services.json` from Firebase (Project settings → Your apps → `com.grocerylist.app`) into `app/`. It's not committed.
2. Open the project in Android Studio and press Run.

The server needs `YOUTUBE_API_KEY` and `ANTHROPIC_API_KEY` set as environment variables on the Vercel project.
