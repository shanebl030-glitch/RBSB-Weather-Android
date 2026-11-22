# RBSB-Weather for Android — Session transcript

Created By Shane BlackbuRn

## 0.1.0 (2026-11-22)

**Shane:** "mind you a .apk break down for the phone maybe a way to go. is that
possible. don't start just advise me"
→ I recommended a native Android app (Kotlin with Jetpack Compose), staged
0.1 → 0.4.
→ **Shane:** "RBSB-Weather Desktop version and RBSB-Weather Android Version will
be two separate apps".
→ **Shane:** "okay start the android version".

**Decisions (Shane):**
- App ID: `io.github.shanebl030glitch.rbsbweather`.
- Android 10 and newer (minSdk 29).
- Building: he first chose to allow the build sites. claude.ai's network setting
  wasn't available in this kind of session, so GitHub Actions builds it instead.
- He created the empty `RBSB-Weather-Android` repository himself; I'm not
  allowed to create repositories.

**How it's built:** this workspace can't download Android tools (Google and
Gradle are blocked), so every compile and test run happens on GitHub Actions:

1. I push;
2. GitHub runs the unit and Robolectric screen tests;
3. GitHub builds the signed APK;
4. I read the results and the logs through the API.

The `main` branch only has the README. All the work is on
`claude/android-0.1.0`.

**Problems found while testing:**
1. **First CI run:** everything built and 15 of 16 tests passed. The one
   failure was a typo in the test (it set the wrong field name), not in the
   app. Fixed.

Created By Shane BlackbuRn
