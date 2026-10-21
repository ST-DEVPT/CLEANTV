# Setup guide

The full debloat and launcher guide from [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv),
kept here as reference. For this fork's install and plugin steps start with the [README](README.md).
The tested lists per TV model are in [examples/README.md](examples/README.md).

## How it works

Android TVs have the same debug bridge as Android phones. Switch on USB debugging in the TV's
developer options, allow your computer once, and the rest is standard Android tooling:

| Step | What happens |
|---|---|
| **Back&nbsp;up** | The list of installed packages and settings. |
| **Disable** | The manufacturer's telemetry, ad services, content rows and preloaded junk, with `pm disable-user`. Nothing is uninstalled, so a restore script can undo every step. |
| **Install** | A small native launcher, made the default home screen. |

**No root, no cable, nothing uninstalled, and a one-line restore if you change your mind.**

The bloat differs by brand, so the prompt has your coding assistant discover it on your TV and ask
before touching anything you might use.

## Quick start

You need a computer on the same Wi-Fi as the TV and a coding assistant with shell access.

Any of these work: Claude Code (on [0G Private Computer](https://pc.0g.ai) or an [Anthropic plan](https://claude.com/pricing)), [Codex](https://github.com/openai/codex) or [Gemini CLI](https://github.com/google-gemini/gemini-cli).

| Where | What to do |
|---|---|
| **1. On the TV** | Settings → Device Preferences → About → press OK on **Build** seven times → back → Developer options → **USB debugging** on. If you see a Network or ADB debugging toggle, turn that on too. Keep the TV on. |
| **2. On your computer** | Paste [`PROMPT.md`](PROMPT.md) into your assistant and fill in your TV model. |
| **3. Follow along** | Accept "Allow USB debugging?" on the TV, then answer the checklist: which apps you use, which four go in the dock, whether to keep the voice assistant, Chromecast and updates. |

The assistant backs up, debloats, builds and installs the launcher, and checks each step with
screenshots pulled from the TV. With this repo as context it takes about fifteen minutes.

## Supported TVs

Anything running **Android TV** or **Google TV**.

| Platform | What works |
|---|---|
| **Android TV, Google TV** | The debloat and the launcher. |
| **Fire TV** | The debloat half only, since Fire OS will not hand Home to a third-party launcher. And on the 2015 Fire TV Box (Fire OS 5) even that half fails: every `pm` route is refused. |
| **A few China-market TCL sets** | They refuse to install any launcher at all; their lists say so in the header. |
| **Samsung (Tizen), LG (webOS)** | Different systems. See [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) for what a 2021 Samsung set answers over Wi-Fi (no ADB, so no `pm disable-user`) if you are weighing a Tizen port. |

Tested lists from contributors, one folder per model in [`examples/`](examples/), with the full table in [examples/README.md](examples/README.md):

**TCL** 43 · **Xiaomi** 24 · **Sony** 23 · **Philips** 10 · **Hisense** 8 · **Skyworth** 7 · **Amazon Fire TV** 6 ·
**Panasonic** 3 · **Thomson** 3 · **BenQ** 2 · **CHiQ** 2 · **FFalcon** 2 · **Google** 2 · **Haier** 2 · **Hitachi** 2 ·
**Kalley** 2 · **NVIDIA Shield** 2 · **OnePlus** 2 · **onn** 2 · **Redmi** 2 · **Toshiba** 2 · **Vu** 2 ·
AiPro2K · AIWA · Artel · Caxium · Cecotec · Challenger · Compac · Dialog · EKO · Freebox · Insignia Fire TV · JVC ·
Karbonn · Kogan · Motorola · MTC · Noblex · OK. · Polytron · PRISM+ · RCA · Sankey · SANYO · SHARP · Tele System ·
Verizon · Vidda · Vision ·
BYINTEK, JMGO, Portronics, Stellamore, Wanbo and XGIMI projectors

Your TV not here? The prompt discovers the bloat itself. Add your list afterwards and the next
person with your model skips that step: [CONTRIBUTING.md](CONTRIBUTING.md).

## Doing it by hand

Everything the assistant does, you can run yourself. The tools need Python 3 and nothing else.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
```

| Tool | What it does |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client: shell, batch, push, pull. Works where the stock `adb` binary is blocked. |
| [`tools/b64push.py`](tools/b64push.py) | Uploads large files such as APKs in base64 chunks and verifies the checksum. |
| [`tools/relay.py`](tools/relay.py) | Forwards a local port to the TV's ADB port, for assistants whose sandbox can't reach your network. Listens on this computer only. [Guide](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Apply or undo a debloat list, the undo from the state recorded before the change. |
| [`tools/recovery.py`](tools/recovery.py) | Writes the record `disable.sh` needs to undo itself, and prints the restore batch. |
| [`tools/check.py`](tools/check.py) | Compares the TV with the list that was applied, for what a firmware update undid; `--fix` puts it back. |
| [`tools/theme.sh`](tools/theme.sh) | Push a folder from [`themes/`](themes/) to the launcher and restart Home. |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Writes this season's F1 calendar into a theme for race-weekend mode, from the free Jolpica API. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Builds a shrunk release (about 429 KB), installs it, switches Home, and rolls back if Home does not land on it. |

The scripts never leave the TV without a Home app: they only disable a stock launcher they recognise,
and skip the Home app in a list until another launcher answers HOME. Details below.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Every script has a PowerShell twin next to it. On Windows with Android Studio installed nothing
else is needed:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
```

</details>

<details>
<summary><b>How the scripts protect Home</b></summary>

The install scripts ask the TV which launcher owns Home and only disable one they recognise
(`tvlauncher` or `launcherx`); pass `STOCK_LAUNCHER=<package>` (`-StockLauncher`) to override, or
`none` to leave it alone.

`tools/disable.sh` (`disable.ps1`) applies the same rule before it disables anything: if the list
contains the app that owns Home and no other app on the TV answers HOME, it skips that entry, says
why, and disables the rest of the list. So a list applied before the launcher handover cannot leave
the TV with nothing to start. Run the same list again once `launcher/build-and-install.sh` has made
the new launcher Home and the entry is applied; `FORCE_HOME=1` (`$env:FORCE_HOME`) disables it anyway.

</details>

<details>
<summary><b>Where your lists can live</b></summary>

Debloat list paths passed to `disable.sh` and `restore.sh` are relative to the directory you run
from, so your own lists can live outside the repository. Absolute paths also work.

</details>

## The launcher

A small native home screen for Android TV: **about 429 KB**, plain Java, built for TVs with 1 GB of RAM.
No blur, no video, no network permission, no background services.

The **TV** needs Android 8.0 (API 26) or newer: `minSdk` is a compile-time floor in
[`launcher/app/build.gradle`](launcher/app/build.gradle), and the install script asks the TV for
`ro.build.version.sdk` before it builds, so an older set is told both numbers instead of failing at
`pm install` (see [Troubleshooting](docs/TROUBLESHOOTING.md) for what is left on such a set).

| What | How it works |
|---|---|
| **Configure** | Dock order, hidden apps, Live TV app, 4 to 7 tiles per row, reduced motion. All in [`config.xml`](launcher/app/src/main/res/values/config.xml) |
| **Settings on the TV** | Hold OK on the Settings pill for Launcher settings: tiles per row, animations, advanced effects |
| **Themes** | Background, wallpaper and layout from a `config.json` pushed over Wi-Fi, no rebuild. Five ship in [`themes/`](themes/) |
| **24 languages** | Follows the TV's language, with a fully mirrored right-to-left layout for Hebrew |
| **App library** | Optional: browse every installed app and add or remove Home shortcuts. See [the app library guide](docs/app-library.md) |
| **Module cards** | Any TV app can offer its own tile; you approve each one once. See [`docs/modules.md`](docs/modules.md) |
| **Clock screensaver** | A drifting clock over the glow, so nothing burns in |
| **Remote keys** | Keeps the Input, YouTube and gear buttons working after the stock launcher is gone |

<details>
<summary><b>Build it yourself</b></summary>

It needs JDK 17 and Android SDK platform 34. On macOS: `brew install openjdk@17 android-commandlinetools`
and `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. On Linux the install script finds
`~/Android/Sdk` and the `java` on your PATH.

</details>

<details>
<summary><b>Configure</b></summary>

[`config.xml`](launcher/app/src/main/res/values/config.xml) sets the dock order, hidden apps, the Live TV
package, columns (tiles shrink from 5 up), rows under the dock, an optional VPN app for the network pill,
and `reduced_motion` for TVs whose GPU cannot keep up. Themes set the same keys plus a background colour
and a wallpaper.

</details>

<details>
<summary><b>Launcher settings and the default home</b></summary>

- Click **Settings** for the TV's own settings. Hold OK on it and choose **Launcher settings** to set 4, 5,
  6 or 7 apps per row, turn animations on or off, and opt into advanced effects (tile lift, icon zoom and
  smooth ambient-colour transitions; they may use more memory).
- **Follow theme** clears the on-TV choices. Pushing or resetting a theme clears them too.
- **Change the default home:** Launcher settings and the optional Default-home tile
  (`show_home_settings_shortcut`) open Android's home picker. Choose the original launcher to stop using
  CleanYourTV as Home, or CleanYourTV to return. The original launcher must be enabled; if the install
  script disabled it, run the restore script below first. Some TVs provide no home picker.

</details>

<details>
<summary><b>Languages</b></summary>

English, German, Spanish, Catalan, French, Russian, Serbian (Latin), Polish, Czech, Italian, Portuguese,
Turkish, Arabic, Hebrew, Simplified Chinese, Traditional Chinese (Taiwan), Thai, Finnish, Vietnamese,
Bangla, Indonesian, Norwegian Bokmål, Dutch and Uzbek (Latin). The launcher follows the TV's language. Android 13+ can set it for the launcher
alone: `tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales ca'`.

Hebrew mirrors the layout: menu buttons on the left, date and clock on the right with the greeting above,
dock and app rows starting on the right with matching remote navigation, the input selector's icons and
labels on the right, and dates with the month prefix ב (for example, 19 באוקטובר). The Clock screensaver
right-aligns its date and time. Russian includes the module-card screens and the VPN-off badge. Other
languages keep their original layouts.

</details>

<details>
<summary><b>Module cards</b></summary>

Any TV app can declare its own tile (label, caption, glow). The launcher asks you once per app, keeps it
hidden by default, and remembers the answer for that app's signing key and card name. Hold OK on Settings
and choose Module cards to change your mind. How to make one: [`docs/modules.md`](docs/modules.md); the
contract: [`docs/specs/card.md`](docs/specs/card.md).

</details>

<details>
<summary><b>Clock screensaver</b></summary>

Pick "Clock" in the TV's screensaver settings, or over ADB:

```bash
tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'
```

</details>

<details>
<summary><b>Remote keys</b></summary>

- An optional accessibility service keeps the remote's **Input** button working on Google TV
  (`INPUT_KEY_SERVICE=1` at install).
- It can bring back other buttons only the stock launcher answered, such as the **YouTube** button
  (`app_keys` and `input_key_keycodes`, in config.xml or a theme; `HOME_KEY_SERVICE=1` reads `app_keys` too).
- `HOME_KEY_SERVICE=1` also fixes sticks whose Home key sends ALL_APPS, and opens Settings from the remote's
  **gear** button, even one Android does not map (scancodes or keycodes in config.xml).
- With `settings_key_panel` the gear opens a [small quick settings panel](docs/quick-settings-panel.jpg)
  over Home or any app: TV settings, Display, Network, Apps and Launcher settings.

</details>

<details>
<summary><b>Navigation and artwork</b></summary>

Up and Down move one row. The top buttons never fall through to tiles, and Down from them returns to the
dock tile you came from. The last focused app is remembered, and installing or removing an app refreshes
the tiles while Home is open. App banners load at xhdpi or better, so low-density TV UIs do not downsample
them before the focus zoom.

</details>

## Restoring your TV

Keep the list you applied. One line puts the TV back the way it was:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| What | How |
|---|---|
| **A record comes first** | Before `tools/disable.sh` changes anything it writes down what the TV looked like, in `~/.cleanyourtv/`. |
| **Restore replays it** | A package you had disabled yourself stays disabled, and Home goes back to the component that owned it. |
| **The launcher stays installed** | Remove it from the TV's Settings → Apps if you want it gone. |
| **No computer?** | If the TV is unreachable, its own Settings → Apps page can re-enable packages too. |

<details>
<summary><b>What the record holds, and what happens without one</b></summary>

Before `tools/disable.sh` changes anything it writes down what the TV looked like: for every package
in the list whether it was enabled, already disabled or not installed, which component owned Home,
and the three animation scales. That record lives in `~/.cleanyourtv/`, one file per TV and list, and
a second run of the same list keeps the first record instead of saving the cleaned-up state over it.
If you added packages to the list since, that run adds them to the record before disabling them.

Keep the list you applied. The restore script replays the record, so a package you had disabled
yourself stays disabled and Home goes back to the component that owned it:

The `stock launcher` argument is only a fallback: with a record, the launcher that actually owned Home
is the one that gets it back, and the argument is used only when nothing answered HOME at the time
or this project's launcher owned it. The stock launcher is enabled again either way, unless it was
already disabled when the record was taken.
Without a record (an older run, or a record you deleted) the script says so and falls back to
enabling the whole list and resetting the scales to `1.0`, which is what it used to do.

It leaves the new launcher installed (remove it from the TV's Settings → Apps if you want it gone)
and does not know about packages you disabled outside the list. To switch back later, run the
install script again; it works out which launcher to disable even when Home has fallen back to
Android's chooser. If the TV is unreachable, its own
Settings → Apps page can re-enable packages too.

</details>

## After a firmware update

An update installs over the cleaned-up system: the listed packages come back, Home goes to the
stock launcher, and new preloads appear that no list mentions. Nothing else here notices, so
compare the TV with the list you applied:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

It prints what drifted and exits 1 when anything did, so a weekly job on a home server or a Pi is
enough to hear about it:

```bash
0 9 * * 1 cd ~/cleanyourtv && TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

`--fix` reapplies the list to what drifted: it switches Home to the launcher the list does not
disable (pass a package as the second argument if you run a fork), then disables the listed packages
that came back, and reads the TV back to confirm both. It writes no recovery record, because the
record already holds the state from before the first run: `tools/restore.sh` still returns the TV to
stock afterwards. Home is fixed first; if no launcher is left to take it, `--fix` changes nothing
and says so.

It runs the same safety checks as `tools/disable.sh` before it disables anything, since an update is
the moment they matter again: it leaves setupwraith on while the saved Wi-Fi network belongs to it
(`FORCE_SETUPWRAITH=1` on Ethernet), leaves the packages under the HDMI/AV inputs on
(`TVINPUT_ALLOW=1` if you really want them off), and never takes Home to a vendor `FallbackHome`.
Each package it leaves on comes with the reason and exits 1, because the TV is not back to the list
until you deal with that one by hand.

Lists that also disable the updater get no updates at all. To install one, re-enable the updater on
the TV, let it update, run `check.py --fix`, and disable the updater again.

On Windows the same command is `tools\venv\Scripts\python.exe -I tools\check.py <list>`.

## When something goes wrong

The full list of things that bit us is in [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). The
three that matter most:

- 🍎 **macOS blocks `adb connect`** ("No route to host") under Local Network privacy. `tools/tv.py` avoids the binary.
- ⚠️ **Disable the stock launcher only after the new one is installed and running**, or Home lands on a factory test app. The install script orders this for you.
- 🔁 **On Google TV the setup wizard** (`com.google.android.tungsten.setupwraith`) can take Home and crash-loop the system after the stock launcher is disabled. Disable it too, and list it in your `debloat.txt` so restore brings it back. But check `dumpsys wifi | grep -o 'cname=[^ ]*'` first: if the Wi-Fi network was saved during setup it belongs to setupwraith, and disabling it deletes the network (re-join it in Settings first; `tools/disable.sh` skips setupwraith until you do). On Google's own Chromecast and on the Google TV Streamer it also costs phone casting, see [Troubleshooting](docs/TROUBLESHOOTING.md). Stuck already? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Questions people ask

<details>
<summary><b>Is this safe? Can it brick the TV?</b></summary>

Nothing is flashed, rooted or deleted; packages are disabled
in the user profile. The risk is disabling something the TV leans on, which is why lists are
applied in small batches with a restore script ready. The TV's own Settings → Apps page can
re-enable packages even without the tools.

</details>

<details>
<summary><b>What about firmware updates?</b></summary>

Most lists disable the update service, so updates arrive only when
you re-enable it. If one lands, the worst case is a package coming back and you re-run the script.

</details>

<details>
<summary><b>Won't the apps break?</b></summary>

Streaming apps still update through the Play Store, which is left alone.
The launcher just lists what is installed and launches it with intents that have been stable for a decade.

</details>

<details>
<summary><b>Can I do this for someone in another home?</b></summary>

Yes, over a private [Tailscale](https://tailscale.com)
link instead of the same Wi-Fi: either Tailscale on their TV, shared with you, or a computer or Apple
TV at their home routing to it. Never by opening the ADB port on their router. Step by step in
[docs/REMOTE.md](docs/REMOTE.md).

</details>

<details>
<summary><b>Isn't ADB access a security hole?</b></summary>

It is one explicit "allow this computer" tap on the TV, the
same as developing an Android app. Network ADB is plaintext on port 5555, and on firmware with
`ro.adb.secure=0` any device on the LAN gets a shell, so close it when you are done:

1. On the TV: Developer options → turn **USB debugging** and **Network/ADB debugging** off, and
   revoke the USB debugging authorisations.
2. Reboot the TV, then confirm the port is gone from your computer:
   `python3 tools/adb-closed.py <tv ip>` must say the port is closed. With `nc -vz <tv ip> 5555`,
   only "Connection refused" counts: on macOS, Local Network privacy can block `nc` and make it
   fail with "No route to host" even while the port is open.

The launcher keeps working without debugging.

</details>

<details>
<summary><b>Can it block ads inside apps too?</b></summary>

Partly, with Android's Private DNS (Android 9 and newer). Point
it at an ad-blocking resolver such as [AdGuard DNS](https://adguard-dns.io) and every app's ad and
tracker domains stop resolving, system-wide, surviving reboots. The trade-off: that resolver then
sees every DNS query the TV makes.

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

It does not remove YouTube ads, which come from the same servers as the videos. If an app stops
playing or the TV reports "No internet" (the resolver is down or unreachable), switch back with
`tools/tv.py 'settings put global private_dns_mode opportunistic'`.

</details>

<details>
<summary><b>AirPlay?</b></summary>

Not from this repo. Chromecast built-in stays for Android phones. For iPhones,
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) works; an install
script for it is in [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

</details>

<details>
<summary><b>Can I just download the APK?</b></summary>

Build it with the one-line script; it takes a minute and you
control what goes on your TV. No prebuilt binaries.

</details>
