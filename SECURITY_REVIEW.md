# Security and concurrency review — 19 October 2026

This review covers the tracked Android application, shared/data/feature modules, SQLDelight queries
and migrations, buildSrc plugins, release script, GitHub Actions and user documentation. The initial
working tree was clean. Source inspection, targeted regression tests, credential-pattern scanning and
an advisory lookup were used. This is a repository review, not a proof that every possible defect is
absent. Device/radio tests and production release execution are separate validation boundaries.

## Findings and changes

| Severity | Finding and trigger | Change |
| --- | --- | --- |
| High | Production `CarAppService` accepted any connecting host. | Release builds use AndroidX host validation and its certificate allowlist; development hosts remain available in Debug. |
| High | GitHub publishing/back-merge tasks passed the PAT in `curl` arguments; CI stored it in the Git remote URL. Gradle process logging and Git configuration could expose it. | An HTTPS client sends credentials only as headers, rejects redirects, checks status codes and limits response size. CI uses a masked environment token and GitHub CLI credential helper. |
| High | GitHub release/branch automation embedded the upstream repository destination. | Validated `GITHUB_REPOSITORY`/`github.repository` configuration selects the repository. Release tasks attach only the release variant with the filename expected by the update checker. |
| Medium | Release builds silently used the debug signing key when release credentials were absent. | Missing credentials produce an unsigned artifact. The existing PowerShell publisher still verifies the permanent certificate. |
| Medium | Concurrent deletion requests could mark all remaining vehicles as deleting, while cached state still allowed both operations. | Choosing a surviving current vehicle and marking deletion now occur in one transaction; selection checks reject deleting vehicles. |
| Medium | Simultaneous spare/monowheel lookups used `MutableMap.getOrPut`, creating multiple components with separate monitoring state. | `ConcurrentHashMap.computeIfAbsent` provides one component per auxiliary location. Unsupported wheel requests use an always-enabled precondition. |
| Medium | An unassign confirmation could delete a newer sensor binding; repeated front/rear taps could undo a swap before its timestamp was saved. | Unassign checks the reviewed sensor in its transaction. Swap eligibility, writes and cooldown timestamp commit together. |
| Medium | Concurrent restores shared one safety-copy file and one journal; disposing the screen could interrupt the restart after settings changed. | Backup operations share a mutex; the restore/journal/restart sequence finishes after confirmation despite UI cancellation. AtomicFile also reads the safety copy. |
| Medium | Every received BLE packet launched another potentially suspended coroutine. A burst could grow memory without bound. | Callbacks use nonblocking sends to a 256-entry queue; incoming overflow is discarded. |
| Medium | Broadcasts with changing sensor IDs/addresses could grow inventory/address storage without limit and make bounded backup validation fail. | Automatic discovery stops adding inventory at 256 entries; explicit inventory/assignments still work. Radio identity tracking is capped at 1,000 addresses. Existing stored entries are not deleted. |
| Medium | Every fresh installation inherited the same default vehicle UUID, allowing unrelated default vehicles to match during restore. | Fresh database creation assigns a random UUID. Existing IDs are preserved for backup/NFC compatibility. |
| Medium | The schema-3 callback updated all bindings/readings of a vehicle for each old wheel, corrupting wheel identity or raising uniqueness errors. | It converts each assignment from a snapshot, resolves collapsed motorcycle/trailer slots deterministically, and maps history in one CASE update per vehicle, including unassigned history. |
| Low | Bitwarden's temporary master-password file remained until the Gradle daemon exited. | The file is deleted in `finally` after the CLI operation; JVM-exit cleanup remains a fallback. |
| Hardening | Workflow settings were substituted directly into shell source, action tags were mutable, and the wrapper distribution lacked a pinned checksum. | Settings use quoted environment variables, action references use resolved commit SHAs, and the Gradle archive has an official SHA-256 pin. PR builds select the head SHA and do not persist checkout credentials. |
| Hardening | The phone app did not explicitly apply the version catalog's target SDK; private receivers relied on defaults. | The target SDK is set explicitly, cleartext traffic is disabled, and internal receivers explicitly declare `exported=false`. |
| Low | Failed builds automatically published build scans, and resource-report names grew with the whole task list. The report writer could still be writing when shutdown read it. | Build-scan publishing requires `--scan`; report filenames have a bounded prefix and hash; shutdown joins the writer before reading. Summary labels contain task names, not command-line option values. |

The start-version workflow now also enforces its documented patch bump for hotfixes. The build,
publishing, privacy, backup, Android Auto, troubleshooting and test instructions were reconciled with
these changes. The 13 pre-existing main-module Detekt magic-number findings were replaced with named
colour/time constants. No application/schema version was bumped and no release was published.

Principal change locations:

- [Android Auto host boundary](feature/android-auto/src/main/kotlin/com/masselis/tpmsadvanced/feature/androidauto/endpoint/CarAppService.kt),
  [phone build settings](app/phone/build.gradle.kts), and [Gradle scan settings](settings.gradle.kts).
- [GitHub HTTPS client](buildSrc/src/main/kotlin/com/masselis/tpmsadvanced/github/GithubApi.kt) and
  [CI credential setup](.github/scripts/configure-release-git.sh).
- [Vehicle transactions](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/interfaces/VehicleDatabase.kt),
  [sensor transactions](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/interfaces/SensorDatabase.kt),
  and [auxiliary component cache](feature/main/src/main/kotlin/com/masselis/tpmsadvanced/feature/main/usecase/FindTyreComponentUseCase.kt).
- [Backup coordination](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/interfaces/ConfigurationBackups.kt),
  [schema-3 migration](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/interfaces/DatabaseMigrations.kt),
  and [fresh database identity](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/ioc/Bindings.kt).
- [BLE queue](data/vehicle/src/main/kotlin/com/masselis/tpmsadvanced/data/vehicle/interfaces/impl/BluetoothLeScannerImpl.kt)
  and [radio persistence bounds](data/vehicle/src/main/sqldelight/com/masselis/tpmsadvanced/data/vehicle/SensorLibrary.sq).

## Injection and input checks

- There is no application WebView, HTML execution sink or JavaScript bridge in the reviewed runtime
  code. Compose `Text` renders vehicle names, packets and release notes as text. No executable XSS
  path was identified; HTML-looking input is not a reason to remove legitimate text from those fields.
- SQLDelight queries and raw migration statements bind external values as parameters. The alert-history
  LIKE pattern is a bound value, not SQL text. No SQL injection path was identified.
- Runtime code does not execute a shell. Gradle Git operations use argument lists; semantic versions
  and repository names are validated. Workflow shell inputs use quoted environment variables.
- The existing CSV encoder quotes fields and neutralizes formula prefixes; JSON/JSONL use serializers.
  Backup imports already enforce size, format, UUID, ownership, wheel, numeric and configuration bounds.
  Deep links validate scheme/authority/UUID and require an existing vehicle. QR parsing matches entire
  supported codes. Decoder entry points reject malformed/truncated advertisements.
- The update checker already limits responses and pages, refuses redirects, checks HTTPS URLs against
  the expected TyrePing repository, and requires an explicit user action. This allowlist was retained.
- Existing immutable pending intents, private service/provider boundaries and the debug-only simulator
  were reviewed. Privileged Bluetooth/system broadcasts remain exported where Android delivery requires it.

## Constants and configuration

No matching private-key blocks, GitHub tokens, Google API keys or AWS access keys were found in the
tracked text-file scan. This is a pattern scan of the current tree, not a historical secret audit.
Signing material remains outside Git in ignored files.

Protocol UUIDs/packet layouts, package identifiers, safety bounds, default user settings, the official
update URL and the permanent release certificate fingerprint are intentional constants. Removing them
or making the trust anchors user-controlled would weaken validation. Deployment repository selection
is configurable; the default vehicle identity is generated. Old migration snapshots and test fixtures
retain historical constants so existing data and regression cases remain reproducible. Existing English
UI text and fixed layout values are not credentials or deployment configuration.

## Remaining risks and validation boundaries

- Supported BLE advertisements have no sender authentication. A nearby transmitter can spoof IDs,
  addresses and plausible readings or interfere with reception. Bounds limit resource growth; they do
  not authenticate measurements. Pecham's legacy address-hash sensor ID also has collision potential;
  changing the persisted sensor-key format requires a separate compatibility migration.
- Historic installations/backups retain the original seeded UUID. Importing one of those backups still
  replaces that matching vehicle. The confirmation describes this merge; users must review it.
- Manual configuration/diagnostic exports are plaintext. Android system backup is enabled. Optional
  Firebase-configured release builds collect crash reports and app diagnostic logs; see
  [data handling](app/phone/src/main/play/PRIVACY.md). No claim of encrypted exports or no telemetry is made.
- Radio bounds do not bound lifetime reading history, which intentionally defaults to keep everything.
  Configure retention and review cleanup for long-running installations. Automatic inventory admission
  limits do not remove existing inventory that already exceeds the threshold.
- Review cannot establish phone/OEM lifecycle timing, actual head-unit host behavior, radio-flood
  behavior or audible alert delivery from host tests. Schema-3 data already corrupted by a previous
  installed build cannot be reconstructed by this change.
- Secret-bearing release jobs must run trusted source. Pinning actions and avoiding command-line
  secrets does not isolate secrets from arbitrary build code or a compromised local account. Existing
  Gradle caches, Android dependencies and signing files remain part of that trust boundary.
- OSV's batch API returned no advisories for the 68 directly versioned Maven library entries queried
  from `gradle/libs.versions.toml`. That lookup excludes BOM-selected/transitive artifacts, Gradle
  plugins and npm dependencies, and is not a full software-composition audit.

## Validation

Current execution results are recorded at the top of [TESTING.md](TESTING.md). New regressions cover
release credential isolation and injection rejection, HTTP redirects/status/size limits, concurrent
auxiliary component lookup, stale unassignment, repeated swaps, concurrent deletion, old migration
mapping and bounded radio persistence. Android instrumentation requires a device/emulator.

## References

Host validation follows the [AndroidX HostValidator contract](https://developer.android.com/reference/androidx/car/app/validation/HostValidator).
Workflow input handling follows [GitHub's script-injection guidance](https://docs.github.com/en/actions/concepts/security/script-injections).
The direct-library advisory lookup used [OSV's batch API](https://google.github.io/osv.dev/post-v1-querybatch/).
