# KeptWidget

Open-source, widget-first Android client for your own [Kept](https://github.com/ericerkz/kept) server. Inspired by Google Keep, not affiliated with Google or Kept.

## Features — v0.2.0

**Four home-screen widgets:**
- **Note collection:** Resizable, scrolling list of cards, note titles, previews, checklist rows and pinned badges; tap notes to edit or tap checkboxes to complete items.
- **Two-column grid:** Resizable collection widget with Google Keep-style side-by-side cards. Includes the same quick-add, refresh, and filter settings.
- **Single note:** Select a note or checklist when adding the widget; its contents scroll directly on the home screen. Complete checklist items without opening the editor.
- **Quick capture:** Compact shortcuts for a new text note or checklist.
- Choose **all notes**, **pinned**, or an individual **label** per collection widget.
- Manual refresh plus Android-scheduled updates (nominally every 30 minutes; Android may delay them). Encrypted offline snapshots keep previously loaded notes visible.

**Android app:**
- Native two-column notes grid (three columns on wider Fold/tablet displays), search, all/pinned/checklist/label filters, and note preview colors.
- Create/edit text notes and checklists, mark items done, reorder by editing rows, add/remove items.
- Pin/unpin, set note background color, assign one or multiple labels, archive and move notes to Kept's trash.
- **Share text from other Android apps:** use Android's Share action → Kept Widget to open a prefilled note for review.
- Login to your Kept server using username/password with optional TOTP; session and note caches encrypted with Android Keystore.
- Opens the full Kept web editor for advanced features.

## Download an APK

1. Open [Actions → Android APK](../../actions/workflows/android.yml).
2. Open the latest successful run on **main**.
3. Prefer the **KeptWidget-0.2.0-signed-APK** artifact if available. This is the upgrade-stable version. If that artifact is missing, only a testing/debug APK is configured.
4. Unzip the artifact, then install `app-release.apk` (or `app-debug.apk` for testing).
5. Launch Kept Widget and sign in with your HTTPS server URL, e.g. `https://kept.mcnannay.com`.
6. From your Android home screen, long-press → Widgets → **Kept Widget** and choose the list collection, grid collection, single-note, or quick-capture widget. You can configure several independently.

**Important upgrade rule:** Android only upgrades apps with the same application ID and signing key. Earlier CI-generated debug APKs used ephemeral keys and **cannot be upgraded to the new stable release**. Uninstall the debug APK once, install the stable release APK, then future stable releases signed with that exact keystore can update in place. Back up any local-only data before uninstalling; Kept server notes remain on your server.

## Persistent release signing (required once)

The GitHub Actions workflow builds an unsigned/debug APK for testing on every push. If both repository secrets below are set on the default branch, it ALSO creates a persistently signed release APK.

Go to **Settings → Secrets and variables → Actions → New repository secret** and add:

- `KEPTWIDGET_KEYSTORE_B64`: base64-encoded contents of the **private** JKS keystore.
- `KEPTWIDGET_KEYSTORE_PASSWORD`: the JKS store/key password.

The keystore **must** contain a key with alias `keptwidget`. Do not commit the JKS or the secrets to this public repository. Keep an independent secure backup of the keystore and password, as losing them makes in-place updates impossible.

For this owner's installation, the encrypted signing backup bundle with the initial keystore and secret values was delivered privately in the associated ChatGPT conversation. The ZIP is **not stored in this repository**. Extract its `github-actions-secrets.txt`, copy each value to the corresponding GitHub repository secret, then delete the plaintext file from your machine when done. Keep the JKS backup securely.

Once secrets are configured, run the workflow manually (Actions → Android APK → Run workflow), and download the **signed** APK artifact. Future release builds should keep using the same signing key.

## Security

- Kept session token and offline note previews are encrypted using AES-256-GCM through Android Keystore.
- No hardcoded Kept credentials, tokens, or host-specific secrets in source. The initial server URL is just a default and can be changed.
- HTTPS is required, redirects are disabled for authenticated requests, and no external analytics/ad SDKs are included.
- Password is only used for login. Changing server or signing out clears stored credentials and cached content.
- Auth supports Kept's native password + TOTP login; **OIDC-only sign-in is not supported**.
- The widget uses Kept's authenticated REST API, not the MCP endpoint. No paid Kept Android app, API key, or MetaMCP connection is needed.

## Limitations

This is an independently implemented **Google Keep-style widget and basic notes client**, not a complete 1:1 replacement for every Google Keep/Kept app feature.

- Images, drawings, reminders, rich text authoring, collaboration controls, and locked-note editing still require Kept's web editor.
- Body editing converts the plain text field to HTML on save; if rich formatting would be lost, the editor warns before saving.
- Offline notes display from encrypted cache, but changes are only sent when online; there is currently **no queued offline write sync**.
- Widget behavior depends on Android launchers, and the project has been compiled in GitHub Actions but still requires device testing.
- Debug builds are not update-stable. Use the signed release artifact after configuring GitHub secrets.
- Keep upgrading the server if its API changes; this project uses supported native API endpoints but is maintained independently.

## Local build

Requires JDK 17, Android SDK Platform/Build Tools 35, Gradle 8.10.2.

```bash
gradle :app:assembleDebug
```

Produces `app/build/outputs/apk/debug/app-debug.apk`. For a release, configure environment variables `ANDROID_RELEASE_KEYSTORE` and `ANDROID_RELEASE_PASSWORD` for the permanent JKS, then run `gradle :app:assembleRelease`.

## Key source files

- `KeptApi.java`: Kept REST integration, writes, and checklist item toggles
- `SecureStore.java`: encrypted session and note snapshots
- `NotesActivity.java`: native notes home screen, search, filters, and card actions
- `NoteEditorActivity.java`: note/checklist editor and organization
- `NoteRemoteViewsService.java`: scrolling collection widget
- `SingleNoteRemoteViewsService.java`: scrolling single-note widget
- `CollectionWidgetProvider.java`, `GridCollectionWidgetProvider.java`, `SingleNoteWidgetProvider.java`, `CaptureWidgetProvider.java`: widget providers
- `WidgetRouterActivity.java`: Android-safe widget click dispatcher
- `WidgetConfigActivity.java`, `SingleNoteConfigActivity.java`: widget configuration
- `ShareReceiverActivity.java`: Android share target
- `.github/workflows/android.yml`: GitHub build, debug APK, and stable signing workflow
