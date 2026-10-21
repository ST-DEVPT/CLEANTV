# Plugins and releases

This fork adds two things to CleanYourTV:

1. **The launcher APK is built on GitHub.** No local Android SDK needed.
2. **A plugin store in the launcher.** Publish a theme on GitHub, install it from the TV.

## Getting the APK

Every push to `main` that touches `launcher/` runs [`release-apk.yml`](../.github/workflows/release-apk.yml)
and publishes a release. The newest APK is always at:

```
https://github.com/ST-DEVPT/CLEANTV/releases/latest/download/cleanyourtv.apk
```

```bash
curl -LO https://github.com/ST-DEVPT/CLEANTV/releases/latest/download/cleanyourtv.apk
adb install -r cleanyourtv.apk
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
```

You can also run the workflow by hand: Actions → Release APK → Run workflow.

### Signing key (do this once)

Android only installs an update over an app signed with the same key. Without a fixed key each CI
run signs with a new throwaway key, and every update needs an uninstall first. Make one key and give it
to GitHub as secrets:

```bash
keytool -genkeypair -keystore cleantv-release.jks -alias cleantv -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=CleanTV"
base64 -w0 cleantv-release.jks > cleantv-release.jks.b64
```

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | contents of `cleantv-release.jks.b64` |
| `SIGNING_STORE_PASSWORD` | the keystore password you typed |
| `SIGNING_KEY_ALIAS` | `cleantv` |
| `SIGNING_KEY_PASSWORD` | the key password (same as the store password unless you set another) |

Keep the `.jks` file out of the repo and backed up. Lose it and TVs need an uninstall to take new builds.

## Plugins

A plugin is a theme folder under [`themes/`](../themes/README.md) with a `plugin.json`:

```
themes/my-theme/
  plugin.json      required
  config.json      required: the theme keys, see themes/README.md
  wallpaper.jpg    optional
  wallpapers/      optional: several images, rotated hourly
  preview.jpg      optional: not installed
  README.md        optional: not installed
```

```json
{
  "name": "My theme",
  "version": "1.0.0",
  "description": "One line shown in the store.",
  "author": "you"
}
```

The folder name is the plugin id: lowercase letters, digits and `-`.

### Publishing from the GitHub web UI

1. Open the repo on GitHub → `themes/` → **Add file → Upload files**.
2. Drag in a folder with the files above (or create `themes/<id>/plugin.json` with **Create new file**).
3. Commit to `main` (or open a pull request: the Plugins workflow validates it without publishing).

[`plugins.yml`](../.github/workflows/plugins.yml) then runs `tools/build_plugins.py`, zips every plugin,
writes `catalog.json` and uploads them to the `plugins` release. TVs see it next time the store opens.

To ship an update, change the files and **bump `version`**. To unpublish, delete the folder.

Check a plugin before you push:

```bash
python3 tools/build_plugins.py            # validate
python3 tools/build_plugins.py --out dist # also build the zips and catalog.json
TV_HOST=192.168.1.50 tools/theme.sh themes/my-theme   # try it on a TV over ADB
```

### Installing on the TV

Home → long-press Settings (or the remote's Settings key) → **Launcher settings → Plugins**. Pick one,
press Install. The launcher downloads the zip, checks its SHA-256 against the catalog, writes the theme
and reloads Home. **Remove plugin** returns to the built-in look.

The store is the only part of the launcher that uses the network, and only while it is open. A zip can
only contain `config.json`, `wallpaper.jpg` and `wallpapers/<image>`; anything else is refused before
the current theme is touched.

Only release builds from GitHub know the catalog address. A local build shows "This build has no plugin
catalog" unless you pass `-PpluginCatalogUrl=...` to Gradle.
