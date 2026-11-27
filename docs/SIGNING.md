# Signing RBSB-Weather for Android

Created By Shane BlackbuRn

## Why this changed (0.7.2)

**Up to 0.7.1, every build was signed with a test key that is in this public repository.**
- Anyone could take that key and sign an app that Android would accept as an update to RBSB-Weather.
- From 0.7.2, releases are signed with Shane's own **release key**. It is never in the repository.
- The test key still signs the test builds of branches (so they install over each other), but never a release.

## Do you need to uninstall? No, and your data is kept

Android only installs an update signed by the same key as the installed app, or by a key that the installed app's key has handed over to. 0.7.2 uses that hand-over: **APK key rotation** (APK Signature Scheme v3, Android 9 and newer; RBSB-Weather needs Android 10).
- The release is signed with the release key, and carries a "lineage": a record, signed by the test key, that the test key hands over to the release key.
- So 0.7.2 installs over 0.6.2, 0.7.0 or 0.7.1 like any update:
  - no uninstalling;
  - places, settings, API keys, the widget and permissions all stay;
  - the in-app "Check for updates" works too.
- **Once a phone has 0.7.2, Android refuses any update signed only with the test key.** The test key keeps only what the hand-over needs:
  - "installed data";
  - "permission". The app has one signature-level permission, added by AndroidX for its own internal broadcasts. Without this capability, Android refused the update on the emulator (`INSTALL_FAILED_DUPLICATE_PERMISSION`).
  - Rollback, shared user and auth are switched off for it.

**Tested on emulators** with a throwaway release key and the Release workflow's exact signing commands (the "Key rotation check" on the `repro/key-rotation` branch), separately on Android 10, 11, 12, 13, 14 and 15:
1. **Install 0.6.2 from GitHub, with a place added.**
2. **0.7.2, signed this way, installed over it:** "Success", and the place was still there on every version.
3. **Updates an attacker could make, all refused** with `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`:
   - signed only with the public test key;
   - signed with another key;
   - signed with the attacker's own hand-over from the test key, with every capability switched on.
4. **A later release signed with the release key** installed: "Success". The test-key build was still refused after it.
5. **A fresh install of 0.7.2** works.

**Which signer each version uses:** the release is signed with `--rotation-min-sdk-version 28`, so the v3 block holds the release key for Android 9 and newer, and there's no v3.1 block. The app needs Android 10, so there are no v1 or v2 signatures either. `apksigner verify` for each version from 29 to 35 (Android 10 to 15, including 12L) shows only the v3 signature, made by the release key. **No version relies on the test key.**

**The one permission the hand-over keeps for the test key:**
- **Name and level:** `io.github.shanebl030glitch.rbsbweather.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `protectionLevel="signature"`.
- **Where it comes from:** AndroidX Core declares it in every app (targetSdk 33+). It's only used by `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` on Android 12 and older.
- **Unused in RBSB-Weather:** that call has no callers in the app (checked in the APK's code), and no component is protected by this permission.
- **So a test-key app gains nothing from it.** If future code uses `ContextCompat.registerReceiver`, see "Later" below.

**The one case needing a reinstall:** a phone with a **test build from the `test-builds` branch made after 0.7.2** (test key only) can't take a release over it, and a phone with a release can't take those test builds. From now on, test builds are for emulators; release candidates come from the Release workflow as drafts.

**Later (optional):** once everyone has 0.7.2, a later release can switch the test key's "permission" capability off too (`apksigner lineage --set-permission false`). It must be tested on emulators first.

## Making the release key (Shane, once, on your own computer)

These commands are for your Linux computer. `keytool` comes with Java: `sudo apt install openjdk-17-jre-headless` if it's missing.

1. **Pick a strong password** (20+ characters) and save it in your password manager first.
2. **Make the key**, in a folder outside any Git repository:
   ```bash
   mkdir -p ~/rbsb-release-key && cd ~/rbsb-release-key
   keytool -genkeypair -v -storetype PKCS12 -keystore rbsb-release.p12 \
     -alias rbsb-release -keyalg RSA -keysize 4096 -validity 10950 \
     -dname "CN=Shane BlackbuRn, O=RBSB-Weather, C=NZ"
   ```
   - It asks for the password twice.
   - With PKCS12, the key's password is the same as the keystore's.
   - `10950` days is 30 years.
3. **Note its fingerprint** (not secret; the release checks against it):
   ```bash
   keytool -list -v -keystore rbsb-release.p12 -alias rbsb-release | grep SHA256
   ```
4. **Back it up, at least twice, offline.** For example, a USB stick kept somewhere safe, and an encrypted copy elsewhere. Your password manager can hold the file as an attachment.
   - **If the key is lost,** no further update can be installed over the app: everyone would have to uninstall and lose their data.
   - **If it's stolen,** someone could sign updates as you. Keep it off shared folders and out of email and chats.
5. **Never** put `rbsb-release.p12` or its password in the repository, an issue, a PR or a chat (including this one). The repository's `.gitignore` also refuses `*.p12`, `*.jks` and `rbsb-release*`.

## Giving it to the Release workflow (GitHub, once)

The workflow signs with it on GitHub's servers. GitHub keeps the key encrypted, never shows it again, and hides it in logs.

1. **Create the environment:** on GitHub, open the repository → **Settings → Environments → New environment**, and name it `release`.
   - **Required reviewers:** add yourself, so each release run waits for your approval.
   - **Deployment branches and tags:** *Selected branches*, and add `main`.
2. **Add four environment secrets** (in the `release` environment → **Add environment secret**):

   | Name | Value |
   |---|---|
   | `RBSB_RELEASE_KEYSTORE_B64` | the output of `base64 -w0 ~/rbsb-release-key/rbsb-release.p12` (one long line) |
   | `RBSB_RELEASE_STORE_PASSWORD` | your password |
   | `RBSB_RELEASE_KEY_ALIAS` | `rbsb-release` |
   | `RBSB_RELEASE_KEY_PASSWORD` | your password again (PKCS12) |

3. **Add one environment variable** (**Add environment variable**, not secret): `RBSB_RELEASE_CERT_SHA256`, set to the SHA256 fingerprint from step 3 of "Making the release key", for example `AB:CD:…`.

**Or, from a terminal with the GitHub CLI** (`gh auth login` first):
```bash
cd ~/rbsb-release-key
R=shanebl030-glitch/RBSB-Weather-Android
base64 -w0 rbsb-release.p12 | gh secret set RBSB_RELEASE_KEYSTORE_B64 --env release -R $R
gh secret set RBSB_RELEASE_STORE_PASSWORD --env release -R $R   # paste the password when asked
gh secret set RBSB_RELEASE_KEY_ALIAS --env release -R $R --body rbsb-release
gh secret set RBSB_RELEASE_KEY_PASSWORD --env release -R $R     # the same password
gh variable set RBSB_RELEASE_CERT_SHA256 --env release -R $R --body "AB:CD:…"
```
(Create the `release` environment in the web page first, with yourself as reviewer.)

## Making a release

**Actions → Release → Run workflow** (on `main`), with tag `v0.7.2`. After you approve the run, it:
1. checks the version matches the tag;
2. runs every test and lint (any lint error stops it);
3. builds the APK, signs it with the release key and the hand-over from the test key, and checks the signature: it must be the release key's fingerprint, never the test key's;
4. makes `SHA256SUMS`;
5. creates a **draft** release with the APK, `SHA256SUMS`, the audit report and the release notes.

You check the draft and press **Publish**, which creates the tag.

## Checking a release APK yourself

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs rbsb-weather-android_0.7.2.apk   # from Android's build-tools
```
The signer's SHA-256 must be your release key's fingerprint.
