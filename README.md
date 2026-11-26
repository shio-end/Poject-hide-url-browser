# VEIL

## เปลี่ยนไอคอน VEIL

วางรูป PNG สี่เหลี่ยมจัตุรัส (แนะนำ 512 × 512 พิกเซล) ทับไฟล์ [icons/drawable-nodpi/app_icon.png](icons/drawable-nodpi/app_icon.png) แล้วบิลด์ APK ใหม่ รูปเดียวนี้ใช้ทั้งบนหน้าจอหลักของ Android และหน้าเปิดแอป พื้นที่โปร่งใสจะแสดงเป็นสีดำ

## Publishing an update

Keep the application ID and sign with the same private key used for the published APKs. The SHA-256 fingerprint of the signing certificate is `c0d24cc595b0e55d3e7a807a215668c24737a01aaed49215b141721c7f58156d`. Verify the signed APK with `apksigner verify --print-certs`.

Before building, update `versionCode`, `versionName`, and the short Thai `releaseNotes` summary in `downloads/latest.properties` (UTF-8). Gradle bundles this version and summary into the APK. Place the signed APK in `downloads/Hide-URL-Browser.apk`. Publish the commit to main and its matching tag (for example `v1.0.0`) together. The app reads the feed on main and downloads the APK from the version tag.

## Google sign-in and YouTube playlists

Register an Android OAuth client for `com.hideurl.browser` with signing certificate SHA-1
`F0:BD:4F:6E:48:FB:FF:80:BA:4D:9C:80:B5:24:32:A1:EB:D2:18:5E`.
In the same Google Cloud project create a Web application OAuth client, configure the
consent screen, enable YouTube Data API v3, and configure the youtube.readonly scope.
For a testing project, add the Google accounts that will test consent. For public
distribution complete Google's applicable consent and verification requirements.

Pass the **public Web client ID** via the Gradle property
`-PVEIL_GOOGLE_WEB_CLIENT_ID=YOUR_ID.apps.googleusercontent.com` when building.
Guest browsing works without it. VEIL keeps the selected email on the device.
Playlist authorization is requested separately and its access token stays in memory.
Google sign-in does not create a logged-in YouTube Music WebView session.
