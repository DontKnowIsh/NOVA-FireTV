# NOVA Fire TV

NOVA Fire TV is an **unofficial fork/custom build of the Jellyfin Android TV app**, adapted around a **Netflix-style TV interface** for **Amazon Fire TV and Fire TV Stick**.

The current build is based on **Jellyfin Android TV v0.19.10**.

> **A note from me:** I don't know how to code. I came up with the ideas, tested the app on the TV, reported what worked and what did not, and the code changes for this project were made with **ChatGPT**. I am sharing it because it may be useful to other people. You are welcome to use it exactly as it is, fork it, fix things, change things, or add your own features.

## What NOVA changes

NOVA keeps the Jellyfin server connection, libraries, playback and user data, while changing and extending the Fire TV experience with a **Netflix-style browsing layout and presentation**. The current build includes a NOVA-styled home and library interface, remote-friendly navigation and focus highlighting, customised search and browsing screens, cinema-style themes, and additional playback/UI tweaks developed during testing.

This project is aimed at **Fire TV / Fire TV Stick**. It may also work on other Android TV devices, but that is not the main target.

## Download / install

The APK is provided with the project as **NOVA-FireTV-v1.0.0.apk**.

You can sideload it onto a Fire TV / Fire TV Stick using your preferred sideloading method or ADB. After installation, open NOVA and connect it to your Jellyfin server as normal.

Because this public build uses its own package ID, it is separate from the official Jellyfin Android TV app.

## Want to improve it?

Please do. If you know more about Android/Kotlin/Jellyfin than I do and want to improve the code, fix a bug, clean something up, or add a feature, feel free to fork the repository and make changes. Pull requests and useful fixes are welcome.

If you do not want to change anything, you can simply install the APK and use it as it is.

## Upstream / credits

NOVA Fire TV is derived from the official **Jellyfin Android TV** project:

- Upstream project: Jellyfin Android TV
- Upstream repository: https://github.com/jellyfin/jellyfin-androidtv
- Base version used here: **v0.19.10**
- Upstream licence: **GNU GPL v2.0**

NOVA is an independent community modification and is **not an official Jellyfin project** and is not endorsed by the Jellyfin project.

See [UPSTREAM.md](UPSTREAM.md) for the base-source details.

## Building from source

The GitHub Actions workflow fetches Jellyfin Android TV v0.19.10, applies the NOVA custom files and patch script, then builds the release APK.

The workflow also creates a source archive containing the patched source tree used for that APK so the corresponding source is available alongside the binary.

## Licence

This derivative project is distributed under the **GNU General Public License version 2 (GPL-2.0)**, consistent with the upstream Jellyfin Android TV project. See [LICENSE](LICENSE).

## Disclaimer

This is a hobby/community project shared as-is. There is no warranty. Back up anything important and use builds at your own discretion.
