# NOVA for Fire TV

NOVA is an **unofficial, community-made Jellyfin client for Amazon Fire TV and Fire TV Stick**, built on the official [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) app (v0.19.10). It keeps Jellyfin's server connection, playback and user data, and wraps them in a cinematic, remote-first interface.

> **A note from the creator:** I don't know how to code. I came up with the ideas, tested everything on the TV, and reported what worked and what didn't. The code was written with AI assistants (ChatGPT and Claude). I'm sharing it because it might be useful to others. Use it as it is, fork it, fix it, or add your own features.

---

## ⚠️ Disclaimer — please read

NOVA is **only a player and a remote control** for software you run yourself. It **does not host, provide, link to or recommend any films, shows, channels, playlists or download sources**, and it comes with none built in.

- Use NOVA **only with media you own or have the legal right to access**.
- The optional download features talk to tools you install and configure yourself (Seerr, Radarr, Sonarr, qBittorrent). What those tools fetch, and from where, is **entirely your responsibility**.
- The Live TV section plays playlists or logins that **you** enter. Only use services you are licensed to watch.
- **The NOVA project does not condone piracy or copyright infringement of any kind.** If something is illegal where you live, don't use NOVA to do it.

NOVA is not affiliated with, or endorsed by, Jellyfin, Amazon, Seerr, Radarr, Sonarr, qBittorrent or any other project named here.

---

## What it can do

### Watching
- **Cinematic home and libraries** – a large hero banner with video previews, poster grids with an A–Z letter bar, sorting (A–Z, newest, year, rating), age filters and a "Surprise me" button.
- **The same side bar on every page** – Home, Favourites, Categories, Search, Download, Downloads, Live TV and your libraries, plus *New Movies* and *New TV Shows*.
- **Film and show pages** – Resume / Play from start, Continue SxEy for shows, seasons, cast, related titles and sequels.
- **Music** – artists as a grid, albums and tracks.
- **Themes** – Dark, Cinema Red, Seerr, Midnight, Amber, Emerald and Muted Purple restyle every NOVA screen, including Live TV.
- **Profiles** – pick an avatar for each person on the "Who's watching" screen.
- **Kids lock** – a PIN is needed to play anything rated 15 / R or above (on by default, PIN `0000`, change it in Settings).

### Live TV (IPTV)
- Bring your own **M3U / M3U8 playlist** (with an optional XMLTV guide) or **Xtream Codes login** from a TV service you subscribe to.
- Category pills, channel cards with what's on now, a live preview window, favourites, channel search and full-screen playback with channel up/down.
- Nothing is pre-loaded: you enter your own details on the TV, and they're stored only on that device.

### Requesting and managing downloads (optional)
These only appear if your server is set up for them (see *Optional extras* below).
- **Download page** – search any film or show, browse by genre, studio, network or language, and open a preview with the trailer, description and age rating.
- **Requests through Seerr**, with a season picker for TV shows.
- **Choose a copy** – see the releases Radarr / Sonarr find, sorted by your Custom Format scores, and pick one.
- **Downloads page** – progress, size, speed, seeds/peers and status from qBittorrent, with Pause / Resume, *Other copy* (blocklist and search again) and *Delete*.
- **Delete from the server** – removes a title through Radarr / Sonarr, Seerr and Jellyfin (protected by a PIN).
- **Server storage** – free space shown in Settings.
- Optional **Indian films** setting – adds Hindi and Punjabi to the Download page, and sends Indian-language films to a Radarr root folder with "Indian" in its name.

### Fire TV extras
- **Open NOVA on start-up** – NOVA can come up by itself when the Fire TV turns on or wakes.
- **Navigation sounds** – Fire TV-style clicks as you move around (can be switched off).

---

## What you need

- A **Jellyfin server** (a recent version).
- An **Amazon Fire TV device running Fire OS** (Fire TV Stick 4K Max, Fire TV Cube, older Fire TV Sticks).
  The newest 2026 Fire TV Stick 4K / 4K Select / HD run **Vega OS** and **cannot install Android apps**. Check under *Settings → My Fire TV → About*.
- Other Android TV devices may work, but Fire TV is the target.

## Installing

1. Download **NOVA-FireTV.apk** from the [Releases](../../releases) page.
2. On the Fire TV: *Settings → My Fire TV → Developer options* → turn on **ADB debugging** and **Apps from unknown sources**
   (to see Developer options, open *About* and press OK on the device name seven times).
3. Find the Fire TV's IP address under *Settings → My Fire TV → About → Network*.
4. On a computer with [adb](https://developer.android.com/tools/releases/platform-tools):
   ```
   adb connect <fire-tv-ip>
   adb install -r NOVA-FireTV.apk
   ```
   (Or use the *Downloader* app on the Fire TV with the APK's link.)
5. Open NOVA, connect to your Jellyfin server and sign in.

**Optional:** for *Open NOVA on start-up* on Fire OS 8, also run:
```
adb shell appops set app.nova.firetv.iptv SYSTEM_ALERT_WINDOW allow
```

### First things to set in *Settings → Customization → NOVA*
- **Change delete PIN** and **Change kids lock PIN** (both start as `0000`). The first time you change one, NOVA also asks you to choose an **8-digit master code**, which unlocks any PIN if you forget it. Each TV has its own; there is no built-in code.
- **Theme**, **Navigation sounds**, **Open NOVA on start-up** and **Indian films** as you like.

---

## Optional extras (download features)

NOVA finds everything through your Jellyfin server, so there is nothing to type in on the TV:

1. Install **Seerr** (or Jellyseerr / Overseerr) and connect it to **Radarr** and **Sonarr**.
2. In Radarr and Sonarr, add **qBittorrent** as the download client (for the Downloads page).
3. Install the **JellyBridge** plugin on Jellyfin and enter your Seerr URL and API key in its settings.
4. Sign in to NOVA with a Jellyfin **administrator** account (NOVA reads the JellyBridge settings).

Without these, NOVA works as a normal Jellyfin player and the download pages simply stay empty.

> Again: NOVA does not supply or suggest any indexers, trackers or sources. Configure your own tools responsibly and legally.

---

## Building it yourself

Every push builds the APK with GitHub Actions (*Actions → latest run → Artifacts*). Tagging a version (`v1.2.0`) also publishes a Release with the APK and the full patched source.

The workflow:
1. fetches Jellyfin Android TV **v0.19.10**,
2. copies in the files from `custom/` and `assets/`,
3. runs the patch scripts in `tools/`,
4. builds and signs the release APK.

Forks without signing secrets get a throwaway key, so their APK installs but not over an official build. To sign your own builds, add `NOVA_KEYSTORE_BASE64`, `NOVA_KEYSTORE_PASSWORD`, `NOVA_KEY_ALIAS` and `NOVA_KEY_PASSWORD` as repository secrets.

## Contributing

Pull requests are welcome – bug fixes, clean-ups and new features. Please keep the project free of any content sources, playlists, indexers or instructions for obtaining copyrighted material.

## Credits and licence

NOVA is derived from **[Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv)** (GPL-2.0). See [UPSTREAM.md](UPSTREAM.md).

This project is distributed under the **GNU General Public License version 2**, like the upstream project. See [LICENSE](LICENSE). The full source for every release is published alongside the APK.

NOVA is not an official Jellyfin project and is not endorsed by the Jellyfin team. It is provided as-is, with no warranty.
