# Wraith

Wraith is a local-first desktop program library for GitHub releases. It lists repositories owned by the configured GitHub owners, reads their public `launcher.json`, downloads published GitHub Release assets, installs them locally, and explains how to launch them.

On first launch, Wraith shows a short three-step guide covering browsing, installing, and launching safely. Open **Help** in the sidebar to view it again.

It has no accounts, telemetry, analytics, update server, or non-GitHub network traffic. Runtime requests are limited to read-only `api.github.com` requests and allowlisted GitHub Release download hosts. Cached library data remains available offline.

## Development

Requirements: JDK 17 and an internet connection the first time Gradle resolves JavaFX/build dependencies. A newer JDK can build the project because compilation uses `--release 17`.

### Linux

```bash
cd Wraith
./gradlew run
```

### Windows PowerShell

```powershell
cd Wraith
.\gradlew.bat run
```

## Tests

```bash
./gradlew test
```

## Packages

`jpackage` cannot cross-compile; build each native package on its target OS.

### Linux

Install JDK 17 plus `fakeroot` and `dpkg-dev` (Debian/Ubuntu example):

```bash
sudo apt install fakeroot dpkg-dev
./gradlew clean test packageLinux
```

Outputs: `build/jpackage/app-image/`, `build/jpackage/linux/wraith-app-image.tar.gz`, and `build/jpackage/linux/*.deb`.

### Windows

Install JDK 17 and WiX Toolset:

```powershell
choco install wixtoolset -y
.\gradlew.bat clean test packageWindows
```

Outputs: `build/jpackage/app-image/` and `build/jpackage/windows/*.exe`.

## Local storage

| Platform | Config/logs | Cache | Programs |
| --- | --- | --- | --- |
| Windows | `%APPDATA%\Wraith` | `%LOCALAPPDATA%\Wraith\cache` | `%LOCALAPPDATA%\Wraith\programs` |
| Linux | `$XDG_CONFIG_HOME/Wraith` or `~/.config/Wraith` | `$XDG_CACHE_HOME/Wraith` or `~/.cache/Wraith` | `$XDG_DATA_HOME/Wraith/programs` or `~/.local/share/Wraith/programs` |

`config.json` contains local settings and the optional token; `installs.json` contains local installation records; `wraith.log` holds the newest 500 diagnostic lines. Corrupt JSON is moved to `*.bad` and replaced with fresh local defaults. Tokens are never logged.

## Repository requirements

Wraith lists public repositories for configured owners. A repository becomes installable when it has a GitHub Release and a root-level `launcher.json` that selects one release asset.

```json
{
  "displayName": "Phantom",
  "description": "Minecraft client",
  "icon": "icon.png",
  "install": {
    "type": "mod-jar",
    "asset": "Phantom*.jar"
  },
  "launch": {
    "instructions": "1. Open your Minecraft launcher and pick Forge 1.8.9.\n2. Press Play."
  }
}
```

Supported install types:

- `mod-jar` — opens the Minecraft Detect → Install → Launch wizard.
- `archive` — accepts `.zip`, `.tar.gz`, or `.tgz`, and extracts into the Wraith program folder with zip-slip and symlink protection.
- `file` — installs a single asset. Set `"executable": true` to add the Linux owner execute bit.

Assets can be platform-specific:

```json
"asset": { "windows": "Program*.exe", "linux": "Program*.AppImage" }
```

An optional runnable command is relative to the installed folder:

```json
"launch": {
  "run": { "linux": "bin/app", "windows": "bin/app.exe", "args": ["--safe"] }
}
```

Wraith asks before first launch of each downloaded program. It never executes anything after installing it.

## GitHub, caching, and rate limits

The default owner is `yxzroot`; add comma-separated owners in Settings. Forks and archived repositories are hidden by default. Responses have ETags and a 10-minute cache TTL. GitHub `404` responses for a manifest/release are normal, and `403`/`429` rate limits fall back to cached data.

Set `WRAITH_GITHUB_TOKEN` or enter a token in Settings to raise API limits. The token is sent only to `api.github.com`, is stored locally only when entered in Settings, and is never logged.

## Hyprland and other tiling WMs

Wraith is intentionally fixed at 900×560. When Hyprland/Sway/i3-like environments are detected it uses an opaque shell so the compositor does not blur the desktop through the window. Its intended window class/app id is `wraith`.

Find it with:

```bash
hyprctl clients
```

Then create a float/size/center/noblur rule appropriate to your Hyprland version. For example, some versions accept rules in the shape:

```ini
windowrulev2 = float,class:^(wraith)$
windowrulev2 = size 900 560,class:^(wraith)$
windowrulev2 = center,class:^(wraith)$
windowrulev2 = noblur,class:^(wraith)$
```

Hyprland rule syntax varies by release, so use the output of `hyprctl clients` and the documentation for your installed version. Set `WRAITH_DEBUG=1` to display the detected class, OS, config path, and JavaFX version in Wraith’s footer.

## Troubleshooting

- **Forge is not detected:** Browse to the profile folder, ensure Forge 1.8.9 is installed, then rescan. Wraith never installs Forge.
- **Mods folder is not writable:** close Minecraft and check the path/permissions. Flatpak sandbox paths are called out in the error.
- **Flatpak locations:** standard Flatpak Minecraft and Prism instance locations are scanned.
- **Transparent window fails on Linux:** enable **Use system window frame** in Settings, then restart Wraith.
- **Offline:** cached library entries stay visible, but Install and Update should wait until GitHub is reachable again.
