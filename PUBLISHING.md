# Publishing Shush

Application id: `com.galynte.shush`

The launcher name is **Shush**. A Play listing title that still matches search is **Shush White Noise**.

## GitHub releases

The workflow in `.github/workflows/build-and-release.yml` follows the TapOut pipeline in [Galynte/TapBlok](https://github.com/Galynte/TapBlok).

- Every push to `main` and every tag runs unit tests and builds a debug APK.
- Pushes to `main` also build a release APK and AAB and upload them as workflow artifacts.
- A tag named `1.2.3` (three numbers, no `v`) sets `versionName` and `versionCode`, creates a GitHub Release, and attaches `Shush-1.2.3.apk` and `Shush-1.2.3.aab`.

`versionCode` is `major * 10000 + minor * 100 + patch`, so `0.0.1` is `1`, `0.0.2` is `2`, `0.0.3` is `3`, `0.0.4` is `4`, `0.0.5` is `5`, `1.0.0` is `10000`, and `1.2.3` is `10203`. Play requires this number to increase on every upload. The current release is `0.0.5`.

### Signing secrets

Add these repository secrets before cutting a tag. The names match TapOut.

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | Base64 of the upload keystore (`base64 -w0 upload-keystore.jks`) |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

A tag push without `SIGNING_KEY` fails on purpose, so a public release is not signed with a throwaway debug key. Pushes to `main` still produce an installable APK signed with the CI debug keystore when the secrets are absent. That APK is for trying the pipeline. Do not upload it to Play Console, and do not expect it to update over a later upload-key build.

Keep the upload keystore offline. Play App Signing can hold the app signing key; the keystore in `SIGNING_KEY` is the upload key.

## Play Console

1. Create the app with package name `com.galynte.shush`.
2. Download `Shush-X.Y.Z.aab` from the GitHub release (or build it with `./gradlew bundleRelease` against the upload keystore).
3. Open **Release > Production** (or a test track) and create a release.
4. Upload the `.aab`. If Play asks for native debug symbols, upload the matching `Shush-X.Y.Z-native-debug-symbols.zip` from the same release. Compose pulls in a small native graphics library; the zip is produced only when those libraries are in the bundle.
5. Paste the listing text below and upload the graphics in `docs/play-store/`.

### Listing

**Title:** Shush White Noise

**Short description:** White, pink, and brown noise with a fade-out sleep timer. Offline.

**Full description:**

Shush is a small white-noise machine for naps, night feeds, and any room that needs a steady hush.

Pick one sound and press play.

• White — a steady, full-spectrum hush
• Pink — softer, like rain on a roof
• Brown — deep, like a distant ocean

Set a sleep timer for 15 minutes, 30 minutes, 1 hour, or any length up to 23 hours 59 minutes. When the timer ends, the sound fades out over 12 seconds instead of stopping short.

Playback continues with the screen off. The sound is generated on the phone, so Shush works on a plane and does not ask for an account.

### Data safety

Declare that Shush does not collect or share user data. The privacy policy is [PRIVACY.md](PRIVACY.md). There is no advertising ID, no account, and no network permission.

### Graphics

* `docs/play-store/icon-512.png` — 512×512, full-bleed. Play applies its own mask.
* `docs/play-store/feature-graphic.png` — 1024×500, no transparency.

## Local release build

```bash
./gradlew assembleDebug
./gradlew assembleRelease bundleRelease
```

A debug build creates `~/.android/debug.keystore`. Later release builds on that machine sign with it until you pass an upload keystore:

```bash
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file="$PWD/upload-keystore.jks" \
  -Pandroid.injected.signing.store.password="$STORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias="$ALIAS" \
  -Pandroid.injected.signing.key.password="$KEY_PASSWORD"
```
