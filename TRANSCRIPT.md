# RBSB-Weather for Android — Session transcript

Created By Shane BlackbuRn

## 0.6.2 (2026-11-23)

**Shane:** "I was about to say before you made the change to keep it as is but we can mention it on the GitHub page update"

→ The 0.6.0 updater is restored unchanged: the code is exactly 0.6.0's, apart from the version number.
- The Play Protect warning is explained on the GitHub page: the README, and release notes ready to paste.
- The 0.6.1 test that forbade the install permission was removed with the rest of 0.6.1's changes.
- The checks that guard the updater (checksum, this app and newer, GitHub-only addresses) are back.

**Shane:** "okay time to update GitHub"
→ PR #3 was merged into `main` as a merge commit, keeping all the history. GitHub signs the merge commits it makes itself, as it did for PR #2. Claude's own commits stay unsigned.
- Creating the release isn't allowed from Claude's session, so Shane publishes `v0.6.2` himself, as he did for v0.5.2:
  - the notes from `docs/RELEASE_NOTES_0.6.2.md`;
  - `rbsb-weather-android_0.6.2.apk`, the exact file Shane installed (SHA-256 7293b0fa…0b16);
  - `SHA256SUMS`.

**Shane:** "can we add quite a few snapshots of the RBSB-Weather- Android"
→ Added 19 pictures to `docs/screenshots/`. They're shown on the GitHub page (README → Screenshots) and in the release notes.
- **From the emulator check** (Android 14, 0.6.2 code), which now also photographs each Wind layer: Forecast, Moon, Earth Flat and Globe, live clouds, the three Wind layers, Earthquakes, Settings, Weather Providers, Test connection, Check for updates, About, and sideways.
- **From the screen tests:** the warning banner and screen (with an example warning, labelled as such), the widget, and four animated skies.

## 0.6.1 (2026-11-23)

**Shane:** "First time I have had this when installing on the phone. 'Google Play Protect. Harmful app blocked. RBSB-Weather. This app may be harmful. Installing this app may put your device at risk. Learn more about Play Protect. Install anyway'"

**Cause:**
- The one big change from 0.5.2, which installed without a warning, was in-app updating. That meant the `REQUEST_INSTALL_PACKAGES` permission ("install other apps"), downloading APK files, and handing them to Android's installer through a file provider.
- Play Protect treats a sideloaded app that can install other apps as a possible "dropper", even when the only app it installs is itself.

**Decision:** rather than ask Shane to tap "Install anyway", take the capability out.
- The update check and notifications stay.
- The download goes through the phone's browser, the normal way to sideload. The app shows the published SHA-256 so the file can still be checked.
- 0.6.1 has no install permission and never downloads or installs an APK. A test checks the manifest and the code so it can't come back by accident.

**Not provable here:** whether Play Protect will accept 0.6.1. Its rules aren't published, and there's no Play Protect on the test emulator. Only installing on the phone shows it.

## 0.6.0 (2026-11-23)

**Shane:** "perfect." (He released 0.5.2 as v0.5.2.)
→ Following the roadmap, I went on to 0.6, the last desktop features on the list.

**What was done:**
- **Ported from the desktop:**
  - the update checks (`updates.py`), with Android's installer in place of the .deb;
  - Test connection (`diagnostics.py`);
  - the provider usage readouts;
  - the Wind tab (`wind.py`, `grib2.py` and `wind_panel.py`).
- **New for Android:** the temperature in the status bar, as the phone version of the tray icon.
- **The GRIB2 reader** was checked against the desktop's reader on the same 11 NOAA and test files: every sample point, missing-value count and checksum.
- **The emulator check (real Android 14):**
  - downloaded the real wind map from NOAA, showed it, and the streaks moved;
  - switched all three layers;
  - ran Test connection to the end;
  - checked GitHub for updates against the real v0.5.2 release (there was nothing newer).

**Problems found while testing:**
1. **A test of mine compared rows the wrong way round:** the south-first file is reversed on purpose. Fixed to match the desktop's test.
2. **The new Settings switches only answered a tap on the switch**, the same problem as the globe tick boxes. The whole row now works.
3. **Merging note:** PR #2 was merged after the first 0.6 commit had been pushed to that branch. So `main` and the `v0.5.2` tag include that unreleased code; the released APK is the tested 0.5.2 build (its checksum matches). The rest of 0.6 is on a new branch, `claude/android-0.6.0`.

## 0.5.2 (2026-11-23)

**Shane:** "1. the touch and drag to rotate the globe doesn't work. 2. the globe itself doesn't rotate when you tick rotate slowly."

**Causes (from the code, then proved by tests):**
1. **Drag:** the pinch-to-zoom handler also saw one-finger movement and took it before the drag handler.
2. **Rotate slowly:** each new position cancelled the globe picture being drawn in the background. On a phone a picture takes longer than a frame, so none ever finished while it moved. The tests used a simulated clock, so pictures always seemed to finish in time.

**Proof both ways:**
- New checks were run on a temporary copy of 0.5.1 (branch `repro/globe-turn-0.5.1`):
  - the stress test failed: the view turned 32° but only 1 picture was drawn;
  - on the emulator, neither Rotate slowly nor a drag changed the globe on screen.
- On 0.5.2 the drag check passed straight away.
- Rotate slowly first looked unchanged on the emulator. The screenshots showed my script had tapped the words "Rotate slowly", which didn't tick the box. The words can now be tapped too, and the check was run again (see TEST_REPORT.md).

## 0.5.1 (2026-11-22)

**Shane:** "Do a complete audit on the android because when I click on earth globe it crashes"

**How it was found:**
1. **Code review:** checked the globe's drawing, memory use, background work, and every `!!`,
   range and launched job across the app.
2. **Phone-like stress test** under Robolectric:
   - a 3× screen, animation on, earthquakes, four places, live clouds;
   - the Moon tab first, then the globe hard (zoom to the limit, spin, drag, pinch, double-tap).

   It passed, and it also passed on the exact 0.5.0 code. So the crash needed a real Android runtime.
3. **A real Android emulator on GitHub** (Android 14, Pixel 6 profile), driven by a script the way
   Shane uses the app. On the 0.5.0 build, tapping Earth Globe closed the app within 3 seconds.
   Android's log gave the reason: `IllegalArgumentException: ending radius must be > 0` at
   `EarthScreens.kt:434`, the glow round the globe, drawn before the globe had a size.

**Fixed and verified:**
- **The cause:** every radial gradient is now made safe, and the globe doesn't draw until it's measured.
- **On the emulator, the fixed build:**
  - opens the globe, zooms to 300%, spins and drags;
  - shows live clouds and 38 earthquakes;
  - then goes through every other screen, saves a Moon picture, rotates the phone, and leaves
    and reopens the app, all with no crash.
- **The emulator check now runs on every push.**
- **Crash reports:** added so a future crash on Shane's phone can be shared with its exact reason.
- **Memory hardening:** see CHANGELOG.

**Lesson:** Robolectric doesn't check everything real Android does. Drawing code is now also
tested on a real Android emulator.

## 0.5.0 (2026-11-22)

**Shane:** "now the next update"
→ The next stage on the roadmap: the Moon screen and the Earth map and globe,
with live clouds and earthquakes.

**What was done:**
- Ported the desktop's `earth.py` (where the Sun is overhead, day and night,
  twilight), `globe.py` (the globe projection), `sky.py` (the Sun, Moon and
  stars behind the globe) and `clouds.py` (live clouds, downloaded only when
  newer) to Kotlin.
- Built the Moon, Earth Flat and Earth Globe screens and bottom tabs.
- Ported the desktop's tests for these, using its PyEphem reference file.

**Shane, part-way through:** "use the images attached for icon on RBSB-Weather
Destop and Android" and "continue with the upgrade for Android".
→ The round version is the Android icon (it suits round launcher shapes); the
rounded square is the desktop icon (desktop 2.5.1, its own branch). I cut each
out of the black background with clean edges and made every size needed.

**Problems found while testing:**
1. **Bottom tabs took space,** so three older screen tests no longer found items on
   Robolectric's small default screen. Those tests now use a small modern phone's
   size (360 × 760 dp).
2. **The Flat/Globe switch didn't take a real tap** in the screen test (a semantic
   click worked, but a simulated finger tap, slow tap and tapping its text all
   didn't). I couldn't prove whether a phone would have the same problem, so I
   replaced it with standard tabs, which take a real tap in the test.
3. **The first globe picture was blank** (just the glow). The picture was taken
   before the globe had finished drawing in the background. A new test now draws
   the globe directly and checks it's filled and faces the right way, the
   picture waits for it, and the screen says "Drawing the Earth…" until it's ready.
4. **A new map test compared the wrong places** (sunlit ocean against a desert at
   night, which can be equally dark). It now compares the same desert at noon
   and at midnight.

**Tested:**
- 150 tests run, 150 passed (GitHub Actions run 44990253891).
- APK: checksum, version 0.5.0 (code 6), v2 signature, icon resources at every
  density, Earth maps included.
- Screen pictures of the Moon, Earth Flat and Earth Globe checked by eye.
- Not tested on a phone: gestures (pinch, drag, double-tap), speed of the globe
  and the live-cloud download on a real network.

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

**Shane (23 Nov):** "buy me a coffee to be $1.47 NZD", not as a new version and not announced. About now shows NZ$1.47 and the PayPal link asks for 1.47 NZD, with a new test for the link. There is no version bump or CHANGELOG entry; it ships with the next release.

## 0.7.0: 📍 My location (25 Nov)

**Shane:** "I'd like to add GPS tracking so if I travel to a different suburb or city or country it updates the widget. This is only for the android not the desktop app."

**What I built** (branch `claude/android-0.7.0`):
- Settings → **📍 Use my location** adds a special place, 📍 My location, that follows the phone. It moves once you're more than 1 km away and is named by Android's geocoder.
- **Widgets show my location** makes the widgets and the status-bar temperature show it.
- It updates when the app opens. With **Allow all the time**, the background check (about every 15 minutes) moves it and refreshes the widget while the app is closed.
- Android's own location service is used (no Google Play services). Coordinates are rounded to about 100 m. The desktop app is unchanged.
- The NZ$1.47 coffee change from 23 Nov ships with this version.

**Tested:** see TEST_REPORT.md: new unit, app and screen tests, plus an emulator step that moves the emulator from Auckland to Wellington. A real phone travelling still needs checking by Shane.

Created By Shane BlackbuRn
