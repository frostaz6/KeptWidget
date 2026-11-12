# KeptWidget

An independent, open-source Android client and home-screen widget collection for self-hosted [Kept](https://github.com/ericerkz/kept). Inspired by Google Keep; not affiliated with Google or the Kept app developer.

## What's new in v0.3.0

### More faithful note formatting
- Widget previews use Kept's **noteBody** instead of its flattened **previewText**.
- Text, HTML paragraphs and line breaks remain on separate lines, rather than becoming one run-on sentence.
- Checklist cards no longer show the same flattened text above their checkbox rows.
- More text is visible before truncation, and the main app retains separate lines too.

### Dark mode and adjustable transparency — per widget
- Choose **Dark**, **Light**, or **Follow device** independently for each list, grid, single-note or quick-capture widget.
- **Widget background opacity:** 0–100% slider on all four widget styles.
- **Note card opacity:** additional 0–100% slider for the grid and list collection widgets.
- Colored notes remain colorful, but are mixed with darker tones to keep their text readable.
- All widgets default to dark mode with 88% background opacity and 96% note-card opacity.
- Change the appearance of an **existing widget** from the KeptWidget notes screen → **⚙ → Home screen widgets**. No need to remove and add it again.
- List and grid widgets can independently sort by Kept order (pinned first), recently updated, oldest updated, or title A–Z.

### Safer editing and offline synchronization
- Previously opened **full note** copies are cached securely so they can be edited while offline. Card previews are never used as an editable source because Kept truncates their checklists.
- Newly created notes and existing edits can be placed in an **encrypted offline outbox**.
- Checkbox taps may be queued offline if the clicked item exists in the cached note.
- Android WorkManager retries queued changes once a network becomes available.
- Before applying an offline change, the client fetches the latest server note and compares every field that would be changed. A conflicting newer value **blocks that edit** rather than silently replacing the server content.
- In the notes app, tap **⇅ Sync status** to review queued changes, check conflicts, retry, or explicitly discard an offline edit.
- Signing out warns before discarding pending offline changes.

**Sync limitations:** This Kept server reports noteRevisions=false and idempotentMutations=false. There is no atomic server-side compare-and-swap. The app makes a best-effort field-level conflict check, which reduces lost updates but does not eliminate a race between reading and writing a note. Offline creation is deduplicated by checking the stable syncId before retrying; if the server is unreachable this remains queued. Do not rely on this as your sole archival copy of critical notes.

### Regression checks
GitHub Actions compiles the Android app, runs tests for preserving line breaks, dark colors, and conflict comparisons, and produces a **permanently signed** APK using the repository's private release-signing secrets.

## Widgets

- **List collection:** resizable, scrollable note cards, checklists and quick actions.
- **Two-column grid:** Google Keep-like colored note cards.
- **Single note:** scroll an individual note or entire checklist, toggle items from the home screen.
- **Quick capture:** add a text note or checklist in one tap.
- Per-widget note filters: **All**, **Pinned**, or **Label** (for collections).

## Install / upgrade

1. Open [Actions → Android APK](../../actions/workflows/android.yml).
2. Choose the latest **successful run on main**.
3. Download **KeptWidget-0.3.0-signed-APK**, unzip and install `app-release.apk`.
4. Open KeptWidget, sign in to your server (e.g. https://kept.mcnannay.com) with username, password and optional TOTP.
5. Add widgets from Android's widget picker, or use the shortcuts in KeptWidget.
6. To customize an existing widget, open the notes app and tap **⚙ → Home screen widgets**.

**Upgrade compatibility:** v0.3.0 keeps the application ID `com.frostaz6.keptwidget` and uses the **same private release signing keystore** as v0.2.0. A signed v0.3.0 APK can install over the signed v0.2.0 APK without uninstalling. A debug APK with a different signing certificate cannot upgrade over the release APK.

## Security

- Passwords are used for login but are not saved.
- Kept bearer session token and offline notes/outbox are encrypted with an Android Keystore AES-256-GCM key.
- HTTPS is required. Redirects are disabled for authenticated API calls.
- No ads, analytics SDKs, or hardcoded secrets.
- The signing keystore lives outside this public repository, supplied only via GitHub Actions secrets.
- Changes to the server URL or signing out clear local tokens, cached notes and pending outbox content; sign-out warns about unsent changes.

## Not yet implemented

- Native image insertion, image thumbnails inside the widgets, drawings, and complete rich-text editing.
- Native Android reminder alarms/notifications and recurring reminders.
- OIDC-only login; username/password plus TOTP works.
- The encrypted offline cache doesn't include all media, and the current conflict check is best-effort rather than server-enforced.

Use the full Kept web UI for advanced editing. Device widget behavior still needs verification on a physical phone/launcher.

## Development and signing

This project uses JDK 17, Android SDK Platform 35, Gradle 8.10.2, OkHttp, Android WorkManager, and Robolectric unit tests.

Run locally:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

For permanently signed builds, configure the GitHub Actions repository secrets `KEPTWIDGET_KEYSTORE_B64` and `KEPTWIDGET_KEYSTORE_PASSWORD` with a JKS key alias `keptwidget`. Back up the keystore and password securely. Never commit them.

## v0.4 candidates

Native reminders/notifications, authenticated media previews/attachments, true rich-text editing, and stronger integration tests against a disposable Kept server instance.
