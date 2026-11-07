# Releasing to Google Play from GitHub

The workflow in `.github/workflows/play-release.yml` builds a signed release
bundle (AAB) and APK, then uploads the bundle to Google Play. You set it up
once, then every release is a button press.

## Releasing (every time)

1. Bump `versionCode` (and `versionName`) in `android/app/build.gradle*`,
   commit and push. Google Play rejects a `versionCode` it has already seen.
2. On GitHub, open **Actions > Release to Google Play > Run workflow**.
3. Pick the branch (normally `main`), the track, and the status:
   - **track**: `internal` (just you and your testers), `alpha`, `beta`,
     or `production`.
   - **status**: `completed` rolls the release out straight away; `draft`
     uploads it and waits for you to press **Release** in Play Console.
4. When the run is green the release is in Play Console. The run's
   **Artifacts** section also has the signed AAB and APK to download.

Pushing a tag that starts with `v` (for example `git tag v2.3.6 && git push
origin v2.3.6`) runs the same workflow and uploads to the `internal` track.

## One-time setup

You need three things: your upload keystore, a Google Cloud service account
that Play Console trusts, and five repository secrets.

### 1. Find your upload keystore

This is the `.jks` / `.keystore` file you pick in Android Studio under
**Build > Generate Signed Bundle / APK**, with its passwords and key alias.
Keep using the same one: Play App Signing only accepts bundles signed with
your registered upload key.

Encode it as base64 so it can be stored as a secret:

- macOS: `base64 -i upload.jks | pbcopy` (copies it to the clipboard)
- Linux: `base64 -w0 upload.jks`
- Windows PowerShell:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\upload.jks")) | Set-Clipboard`

### 2. Create a Play service account (do this once for all your apps)

1. Open <https://console.cloud.google.com/>, create (or pick) a project, then
   go to **APIs & Services > Library**, search for
   **Google Play Android Developer API** and press **Enable**.
2. Go to **IAM & Admin > Service Accounts > Create service account**. Name it
   something like `github-play-upload`. Skip the optional role steps.
3. Open the new service account, go to **Keys > Add key > Create new key >
   JSON**. A `.json` file downloads. Treat it like a password.
4. In Play Console (<https://play.google.com/console>), go to
   **Users and permissions > Invite new users**. Paste the service account's
   email (it ends in `iam.gserviceaccount.com`).
5. Under **App permissions**, add each app you want to release this way, and
   tick **Release to production, exclude devices, and use Play App Signing**
   and **Release apps to testing tracks**. Press **Invite user**.

It can take a few minutes (occasionally up to a day) before Google accepts
the new account. If the first run fails with "The caller does not have
permission", wait and run it again.

The same JSON key works for every one of your apps, as long as each app is
ticked in step 5.

### 3. Add the repository secrets

In this repository on GitHub: **Settings > Secrets and variables > Actions >
New repository secret**. Add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the base64 text from step 1 |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | the key alias (Android Studio shows it as "Key alias") |
| `ANDROID_KEY_PASSWORD` | the key password (often the same as the keystore password) |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole contents of the JSON file from step 2 |

If one is missing, the first step of the run fails and names it.

## Troubleshooting

- **"APK specifies a version code that has already been used"**: bump
  `versionCode` and run again.
- **"The Android App Bundle was signed with the wrong key"**: the keystore
  secret isn't your registered upload key. Check Play Console > Test and
  release > App integrity for the expected upload key fingerprint.
- **"Package not found"**: the app isn't ticked for the service account in
  Play Console, or the invite hasn't taken effect yet.
- **"Only releases with status draft may be created on draft app"**: the app
  has never been published. Run with status `draft` until it is.
