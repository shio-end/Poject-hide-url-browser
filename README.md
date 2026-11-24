# Poject-hide-url-browser

## Publishing an update

Keep the application ID and sign with the same private key used for the published APKs. The SHA-256 fingerprint of the signing certificate is `c0d24cc595b0e55d3e7a807a215668c24737a01aaed49215b141721c7f58156d`. Verify the signed APK with `apksigner verify --print-certs`.

Increase the Gradle version code and version name. Place the signed APK in `downloads/Hide-URL-Browser.apk` and record its version code and name in `downloads/latest.properties`. Publish the commit to main and its matching tag (for example `v0.4.0`) together. The app reads the feed on main and downloads the APK from the version tag.
