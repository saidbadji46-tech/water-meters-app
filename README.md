# ADE Meter Reading

A field app for water meter readers. It loads a customer round from the utility's export file, records each reading with its evidence (location, photo, signature), and exports the finished round as files for the office.

**Live app:** https://saidbadji46-tech.github.io/water-meters-app/

The interface is in Arabic (right to left). It installs on a phone as a PWA and is also packaged as an Android app with Capacitor.

## Why it exists

A reader works through a round of customers from a fixed-column text export. In the field the reader has to enter the new index, check it against the previous one, record where the meter is, and hand the round back in a format the office can load. This app does that on one screen per customer.

## Features

**Round setup**
- Imports ADE fixed-column TXT files directly.
- Imports Excel and CSV lists, and plain delimited TXT with a header line such as `name;meter;address;phone;prevIndex`.
- Imports earlier location and warning files, so a customer whose meter was located before shows its position when opened.

**Reading a meter**
- Enters the new reading and shows consumption in m³ and the amount, from the price per m³ set in the app.
- If the new reading is lower than or equal to the previous one, the reading cannot be saved until a reason is chosen from a list.
- Records the meter's GPS position.
- Takes a photo of the meter, or picks one from the gallery.
- Scans the meter's serial number from a QR code or barcode.
- Collects the customer's signature.
- Prints a receipt over Bluetooth.
- Adds a warning code and a note, for example a leak or a damaged meter.

**Round overview**
- Shows round progress, readings done and remaining, zero consumption, consumption above the average and stopped meters compared with the previous round.
- Shows day-by-day progress, consumption compared with the previous round and the highest-consumption customers.
- Shows customer locations on a map and as a heat map, and can sort customers by distance.
- Archives finished rounds and shows the date of the next round.

**Export and backup**
- Exports the full round as a folder with a TXT file and a ZIP. The ZIP holds the readings as Excel, the photos, the locations and a printable report.
- Exports TXT only, ZIP only, a PDF through printing, or shares the round by WhatsApp or e-mail.
- Makes full JSON backups and keeps automatic backups for the last 7 days.

**Device**
- PIN lock.
- Night mode.
- Option to keep the screen awake.

## How it is built

- JavaScript, HTML and CSS in a single-page app (`index.html`)
- Service worker (`sw.js`) and web manifest (`manifest.json`) for installation
- `sqlite-bridge.js` and the `android-plugin` folder for the Android build
- JSZip for the export archives
- Capacitor to wrap the web app as an Android app
- GitHub Pages for the live app
- GitHub Actions to build the Android APK

This build has no database server configured.

## Build the Android APK

The APK is built by GitHub Actions, so no local Android setup is needed.

1. Open the **Actions** tab of this repository.
2. Select the **Build Android APK** workflow.
3. Click **Run workflow**.
4. When the run finishes, download the artifact **ADE-Meter-Reading-debug**.

The APK is built from the local `index.html` and the project assets. More detail is in [README-ANDROID.md](README-ANDROID.md).

## Related

[ADE Kotlin Native](https://github.com/saidbadji46-tech/ADE-Kotlin-Native) is the native Android version of this app, written in Kotlin.
