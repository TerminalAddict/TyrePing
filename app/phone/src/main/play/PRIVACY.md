# TyrePing data handling

Updated 19 October 2026. This describes the Android application's current implementation. TyrePing is
an independent fork maintained in [TerminalAddict/TyrePing](https://github.com/TerminalAddict/TyrePing).
The old upstream website policy did not describe this app.

## Data stored on the phone

TyrePing stores vehicle names and identifiers, sensor assignments, pressures, temperatures, reported
battery values, maintenance information, settings, ride summaries and alert history in its app storage.
Bluetooth discovery can observe nearby device names, addresses and advertisements, including devices
that are not tyre sensors. Diagnostic capture is optional; saved captures and questionable-reading
records can contain raw packet bytes and device identifiers. The compatibility toolkit offers a
separate structural export that omits identifiers and payload bytes.

Ride summaries do not record a GPS route. Older Android versions require location permission for
Bluetooth discovery; this permission requirement does not mean TyrePing records a location history.
Automatic ride/vehicle selection uses the Bluetooth accessory or beacon that you configure.

Automatic sensor inventory discovery stops adding new identities once the inventory has 256 entries.
Explicitly assigned/manually added sensors remain supported. Diagnostic issue groups and the radio
address identity cache are bounded at 1,000 entries each. Reading history and health summaries are
retained until removed through the storage/retention controls; retention defaults to keep everything.

## Network access and external services

Only TyrePing Debug includes the GitHub update checker. It contacts GitHub's public Releases API when
you press **Check now**, without sending sensor readings or requiring a GitHub account token. GitHub
receives the network request, including your IP address. Release builds, including GitHub release APKs
and Google Play bundles, contain no GitHub update checker. Opening a website or licence link (or a
release/APK link in Debug) uses your browser and that site's services.

The project supports optional Firebase Crashlytics. Release builds configured with a Firebase project
enable crash reporting and send diagnostic logs through Crashlytics. Those logs may include sensor
identifiers or packet details. Debug builds disable Firebase collection and use local Logcat logging.
A build without Firebase project configuration does not initialize that reporting service. The QR
scanner uses Google's ML Kit/Play services; component downloads and service operation are subject to
Google's services. Build configuration and installed Google components affect these external services.

## Backups, exports and sharing

Android system backup/device transfer is enabled and governed by your Android account, device and
backup settings; it can include app databases, preferences and saved files. It is separate from
TyrePing's manual configuration backup.

Manual JSON backups contain configuration and identifiers but exclude raw reading history. The last
restore's safety copy remains in app storage. CSV/JSONL exports may include vehicle names, timestamps,
sensor IDs, Bluetooth addresses or raw packets. These exported files are plain text, without encryption
added by TyrePing. They are written to the document provider you select, which may be local or cloud
storage. You control whether to share them; review them first. Deleting app data does not delete copies
you exported to another destination.

Use Storage and retention to review history cleanup, the diagnostics controls to clear captures, or
Android's app-data controls to remove local app data. Manage external exports and system backups at
their respective storage providers. For questions about this implementation, contact the maintainer
through the repository; avoid posting private captures or backups publicly.
