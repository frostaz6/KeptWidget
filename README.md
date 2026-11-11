# KeptWidget

Native Android home-screen widgets for self-hosted [Kept](https://github.com/ericerkz/kept). Inspired by Google Keep's note collection and quick-capture widgets. Not affiliated with Google or Kept.

## Features

- **Note collection widget:** Resizable, scrollable cards with titles, text previews, pinned indicators and up to three checklist lines. Tap a card to edit; tap a checkbox row to toggle its completion.
- **Quick capture widget:** Compact new-note and new-checklist shortcuts.
- Filters for **all notes**, **pinned notes**, or a **specific label**, configurable separately on each widget.
- Manual refresh and automatic widget updates (~30 minutes, at Android's discretion).
- Encrypted offline snapshot for viewing notes when the network is unavailable. Changes require a connection.
- Native note and checklist editor, including a warning before replacing rich-text formatting.

## Download and install

1. Open the [Android APK workflow](../../actions/workflows/android.yml).
2. Open the latest successful run on the main branch.
3. Download the KeptWidget-debug-apk artifact and unzip it.
4. Install app-debug.apk on your Android phone.
5. Launch Kept Widget and sign in using your Kept HTTPS URL, username, password, and optional TOTP code.
6. Tap Add note collection widget, or long-press your Android home screen → Widgets → Kept Widget.

**Note about updating:** CI uses Android's default debug signing key, which may change from run to run. Later APKs may require uninstalling earlier debug builds unless you set up persistent release signing. Do not put keys or tokens into the public repository.

## Authentication and security

- Uses Kept's native JSON API (/api/auth/login, /api/notes, /api/labels), **not** a paid Android client or MetaMCP.
- Passwords are used for login only and not stored.
- The session token and offline note cache are encrypted using Android Keystore AES-256-GCM.
- HTTPS is mandatory. Cross-host redirects are disabled when sending bearer tokens.
- Changing server URL or signing out removes the stored token and cached notes.
- Supports username/password and optional TOTP. OIDC-only sign-in is not supported yet.
- The app has no analytics or ad SDKs.

## Scope and limitations

This is a widget-first client, not a full reimplementation of Kept's Android app. Note/checklist editing and checkbox toggling are provided. Rich text, images, drawings, reminders, shared-note administration, and locked-note editing should be done in the full Kept web UI through the app's shortcut. Note colors are not yet rendered per-note in the collection. Device behavior can depend on launcher; CI compilation alone is not an on-device test.

## Local build

Install Android SDK Platform 35, JDK 17, and Gradle 8.10.2, then run:

    gradle :app:assembleDebug

Output: app/build/outputs/apk/debug/app-debug.apk.

## Source map

- KeptApi.java: REST client (including PATCH requests and checklist mutations)
- SecureStore.java: Android Keystore encrypted session and cached note cards
- NoteRemoteViewsService.java: scrollable widget note cards
- CollectionWidgetProvider.java / CaptureWidgetProvider.java: widget layouts and refresh
- WidgetActionReceiver.java: widget actions and checkbox toggles
- WidgetConfigActivity.java: all / pinned / label widget filter
- LoginActivity.java: self-hosted server sign-in
- NoteEditorActivity.java: notes and checklist editing
- .github/workflows/android.yml: build APK using GitHub Actions
