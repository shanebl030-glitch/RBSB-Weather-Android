# RBSB-Weather for Android — Session transcript

Created By Shane BlackbuRn

## 0.2.0 (2026-11-22)

**Shane:** "It's a good start but there a lot that are missing".
→ I asked what to add first. **Shane** chose: "Warnings + earthquakes, "Moon,
Earth, Globe, Wind", Playback + mini forecast, Weather Providers".
→ Plan: 0.2 Weather Providers, playback and the mini forecast; 0.3 warnings and
earthquakes; 0.4 Moon and Earth; 0.5 Wind.
→ **Shane** (while 0.2 was being built): "The widget are missing as wel".
Widgets were added to 0.2.

**What was done:**
- The five keyed providers were ported from the desktop app's Python, along
  with its normaliser, and tested against the same saved replies the desktop
  tests use.
- Added the provider chain with recovery, the Weather Providers screen, API keys
  in their own private store (excluded from backups), forecast playback, the
  mini forecast in the sky, and the two home-screen widgets.
- Work is on a new branch, `claude/android-0.2.0` (built on 0.1.0). Nothing
  merged or released.

**Problems found while testing:**
1. **First CI run:** didn't compile. Two `kmh` helpers with the same name
   (MET Norway's and the new one). Now there's one.
2. **Second run:** 57 of 60 passed.
   - Tomorrow.io gave 6 days where the desktop app shows 5 (its free plan's
     length). Each provider is now kept to its plan's days. That was a real
     difference from the desktop.
   - Two mistakes in the tests themselves: one read an error that wasn't there,
     and one checked whether a text box was on screen instead of whether it
     existed.
3. **Third run:** 60 of 60 passed.

**Tested:**
- 60 tests run, 60 passed (GitHub Actions run 44970300356).
- The APK was checked here:
  - the checksum matches;
  - version 0.2.0 (code 2), min SDK 29, target SDK 35;
  - both widget receivers and the backup rules are present;
  - it's v2-signed with the test key.
- Not tested on a phone.

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
2. **Second CI run:** everything compiled first time and 27 of 28 tests
   passed. The failing screen test looked for the credit at the bottom of the
   page without scrolling to it; lists only build what's on screen. The test
   now scrolls down to it first.
3. **Getting the APK out:** GitHub's build files ("artifacts") are stored where
   this workspace can't download them. The workflow now also puts the newest
   test build on a `test-builds` branch. It's replaced each time with no
   history, and it isn't a release. I download it from there to check it, and
   so can you.

**Tested:**
- 31 tests run, 31 passed (unit, app behaviour, Robolectric screens, the sky).
- The APK's checksum, zip, manifest (app ID, version, permission) and test-key
  signature were checked here.
- Not tested on a phone yet: there's no device or emulator here.

Created By Shane BlackbuRn
