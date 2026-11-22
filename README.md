# GeoTag Camera — Android app

Free GPS camera app for **assamconverter.com**. Photos and videos get a live stamp (place, address, map,
latitude/longitude, date & time, note, entry number) and go into an entry log with a verify link.

The app opens the GeoTag Camera web app (`https://www.assamconverter.com/geocam/`) and adds:

- real Android permissions for **camera, GPS location and microphone** (microphone only for video sound)
- saving straight to the **Gallery** (Pictures/Movies › GeoTag Camera) and native **Share**
- a built-in **API key** sent as `X-Api-Key` so the website knows requests come from the official app
- update check (Admin → GeoTag Camera → App & API keys → latest / minimum version)
- works on **Android 5.0+**, phone back button, offline screen, opens Maps/WhatsApp links outside

## Build (free, on GitHub)

Every push to the `geotag-app` branch builds a **signed APK** (Actions → *Build GeoTag Camera APK*).
The APK is attached to a new entry under **Releases**.

One repository secret is needed: **`GEOCAM_SECRET`** (Settings → Secrets and variables → Actions).
It unlocks `secrets/release.jks.enc` (signing key) and `secrets/apikey.enc` (API key).
Optional: a **`GEOCAM_API_KEY`** secret replaces the built-in API key.

New version: edit `version.txt` (e.g. `1.1.0`) and push. The build number increases by itself.

⚠️ Keep the signing key backup (`geotag-release.jks` + `GEOCAM_SECRET`) safe. Android only accepts
updates signed with the same key — if it is lost, users must uninstall and reinstall.

## Files

| Path | What |
|---|---|
| `app/src/main/java/.../MainActivity.java` | WebView, permissions, back button, links |
| `app/src/main/java/.../AppBridge.java` | `window.GeoTagApp`: info + API key, save to Gallery, share |
| `app/build.gradle` | version, API key and website address come from the build |
| `.github/workflows/build-apk.yml` | builds and signs the APK |
