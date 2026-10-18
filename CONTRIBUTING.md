## Technical requirements

* [Kotlin](https://kotlinlang.org/)
  with [coroutine & flow](https://kotlinlang.org/docs/coroutines-overview.html)
* Multi-module based on [this approach](https://developer.android.com/topic/modularization)
* Clean Architecture (Entities, Use-Cases, Interfaces)
* [Compose](https://developer.android.com/jetpack/compose)
* IOC with [Metro](https://zacsweers.github.io/metro/latest/index.html)
* Gradle with [Android Gradle Plugin](https://developer.android.com/studio/build)
* Gradle KTS with custom plugins
* Git-flow

## How to build

Clone `https://github.com/TerminalAddict/TyrePing.git` on branch `tyreping`, then run `./gradlew build`. You
don't need the secrets keys to run this project, the Gradle configuration works without them.

## How to try with Android Auto

With a device attached, run `./gradlew desktopAndroidAutoHeadUnit` to start the Desktop Head Unit.
Add `-Pandroid_auto_via_usb` to force the USB transport (`-u`/`--usb`) instead of the default one.

### Secrets (optional)

Release signing, Play Store publishing, Firebase, and GitHub release notes need secrets that are
fetched at build time from a Bitwarden/Vaultwarden vault, via the `bw` CLI (install with
`npm install -g @bitwarden/cli@2026.6.0`). Add the following keys to your gitignored
`local.properties`:

```properties
bitwarden.item=<bitwarden-item>
# Can be "bitwarden.com", "bitwarden.eu" or a custom url
bitwarden.server=<bitwarden-server-url>
bitwarden.email=<bitwarden-account-email>
bitwarden.password=<associated-account-password>
```

Environment variables `BITWARDEN_ITEM`, `BITWARDEN_SERVER`, `BITWARDEN_EMAIL` and
`BITWARDEN_PASSWORD` can be used instead of `local.properties` if needed.

## Publishing

See [PUBLISHING.md](PUBLISHING.md) for the git-flow release/hotfix process.

## Security-sensitive development

Use `:app:phone:assembleDebug` for an installable build without release secrets. Release builds without
credentials are unsigned. `sdk-target` in the version catalog is explicitly applied to the phone app.
Release Android Auto hosts are validated; debug builds permit development hosts.

GitHub Gradle publishing uses `GITHUB_REPOSITORY` in CI or `github.repository` in `gradle.properties`
locally (override locally with `-Pgithub.repository=owner/repository`). Only release APKs are attached.
The HTTP client keeps credentials out of subprocess arguments, rejects redirects, bounds response
sizes and fails on HTTP errors. Treat `secrets/`, local Gradle caches and CI environments as private.
Bitwarden's temporary password file is deleted after the CLI operation, including failure paths.

Use bound SQL parameters, native text rendering and atomic check-and-write transactions for mutations
based on a reviewed state. Do not replace trusted update URL checks or release certificate fingerprints
with arbitrary runtime URLs/keys. Protocol identifiers, package IDs and safety limits are intentional constants.

Run `python3 -m unittest discover -s tools -p 'test_*.py'` (Git and jq are needed for release-script tests),
`./gradlew -p buildSrc test`, and the affected module tests. Device checks and the current review's
validation record are in [TESTING.md](TESTING.md) and [SECURITY_REVIEW.md](SECURITY_REVIEW.md).

Build scans are uploaded only when `--scan` is supplied. Review diagnostic content before explicitly
publishing a scan. Ordinary local builds and failures keep their reports local.
