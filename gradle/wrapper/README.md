## Gradle upgrades

Run `./gradlew wrapper --gradle-version <version> --distribution-type all` for an explicitly chosen version.
Set `distributionSha256Sum` in `gradle-wrapper.properties` to the official checksum for that exact `-all.zip`
distribution, published alongside the archive on `https://services.gradle.org/distributions/`.
Verify the version and checksum together before committing the wrapper update; an old checksum must not
be carried over to a new distribution. Review action commit-pin updates in the same way.
