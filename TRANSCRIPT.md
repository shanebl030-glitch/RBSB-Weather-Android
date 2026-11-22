# RBSB-Weather for Android — Session transcript

Created By Shane BlackbuRn

## 0.4.0 (2026-11-22)

**Shane:** "okay, that looking really godd." (the widget now works)
→ Following his roadmap, I went straight on to 0.4: warnings and earthquakes.

**What was done:**
- I ported `alerts.py` and `quakes.py` to Kotlin.
- I added one shared checker, so the open app and the background check never
  notify the same thing twice.
- I added phone notifications, a background check (Android's WorkManager), the
  banner, badges, tags, chart shading, the widget's warning line, and the
  Warnings, Earthquakes and two Settings screens.

**Problems found while testing:**
1. A compile error: a `continue` inside a lambda isn't allowed in this Kotlin
   version. Rewritten.
2. Two screen tests tapped options that are now further down a longer screen.
   They now scroll to them first.
3. One build failed because Maven Central wouldn't send Robolectric's Android
   jars (twice in a row). The next run worked, and CI now caches them.
4. Taking screen pictures with Compose's built-in capture timed out here. The
   pictures are now drawn straight from the window.

**Tested:**
- 136 of 136 pass (run 44985012092).
- I looked at the screen pictures.
- Not tested on a phone. Still to check there:
  - the live warning feeds;
  - notifications, including with the app closed;
  - the permission request on Android 13+.

## 0.3.1 (2026-11-22)

**Shane:** "The widget doesn't work. Say Couldn't add widget".

**Cause:** in 0.3.0 I used a plain `View` as a spacer in the widget layout.
Android only allows a fixed set of view types in widgets, so the launcher
couldn't load it. My earlier tests drew the widget's pictures but never loaded
the layout the way a launcher does, so they missed this.

**Fix and tests:**
- First I added tests that load the widget the way a launcher does, and
  confirmed they failed with the same error (run 44976546841).
- Then I replaced the spacer with a FrameLayout.
- 101 of 101 now pass (run 44976754434).

**Still to check on your phone:**
- that the widget can be added;
- that its frames loop on your launcher.

If your launcher refuses that many pictures, the widget falls back to a still
picture.

## 0.3.0 (2026-11-22)

**Shane:** "the widget doesn't animate like it does on the desktop version. I want
the android to be a replica of RBSB-Weather desktop version. nothing missing."

**What I told him first:**
- Android home-screen widgets can't run live animation. The nearest is a short
  loop of frames drawn from the real sky.
- I'd list every desktop feature in PARITY.md, then build towards all of them.

**What was done:**
- I ported the desktop's animated scene in full instead of keeping the simpler
  Android sky: `scene.py`, `moon.py`, the Moon renderer, `forecast_strip.py`, the
  icons, and the desktop's playback rules.
- The widget became a looping animation drawn from that scene, with the desktop
  widget's text.
- Retries and staggered start-up now work as on the desktop.
- I wrote PARITY.md, which lists everything still to come with its version:
  0.4 warnings and earthquakes; 0.5 Moon and Earth; 0.6 Wind and the rest.

**Problems found while testing:**
1. **First CI run:** compiled first time; 92 of 94 passed. The sky card is now
   taller, so two screen tests had to scroll down to the parts below it.
2. **Looking at the pictures from the build:** the shade behind the mini forecast
   had a hard edge, because the gradient was round but the shape was squashed.
   It's now drawn squashed as a whole, as on the desktop, and blends in.
3. **Final run:** 98 of 98 passed.

**Tested:**
- 98 tests (GitHub Actions run 44974363240).
- I looked at the pictures of the sky, Moon, mini forecast and widget frames
  that the build saves.
- Not tested on a phone. Still to check there: the animation's smoothness,
  battery use, and whether your launcher shows the widget's frames.

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
