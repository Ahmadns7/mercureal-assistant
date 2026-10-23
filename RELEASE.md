# MercuReal release guide (F-Droid + APKPure + GitHub Releases)

MercuReal is free and open source (MIT). It is not published on Google Play. Distribution is GitHub Releases (signed APK), APKPure (manual upload), and F-Droid (request for packaging, then F-Droid builds and signs from source).

## 1. Signing key (once, never commit it)
Create a keystore: keytool -genkeypair -v -keystore mercureal-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mercureal
Export env vars MERCUREAL_KEYSTORE_PATH, MERCUREAL_KEYSTORE_PASSWORD, MERCUREAL_KEY_ALIAS, MERCUREAL_KEY_PASSWORD, or create keystore.properties at the repo root (storeFile, storePassword, keyAlias, keyPassword). Both are git-ignored. Back up the keystore and passwords: losing it means you can never ship an update under the same package id.
Note: F-Droid signs the APK it builds with its own key, so its copy will not match the APKPure or GitHub APK. That is expected. Users cannot update across the two unless they reinstall. Pick one signing key for APKPure and GitHub and keep it.

## 2. Build a signed release APK
./gradlew assembleRelease
Output: app/build/outputs/apk/release/app-release.apk (signed when the env vars or keystore.properties are set; otherwise it falls back to debug signing, which is fine for testing only, not for APKPure or GitHub).
Check the signature: apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
Native deps must be fetched first: ./scripts/setup_native.sh

## 3. Versioning
versionCode and versionName live in app/build.gradle.kts. Bump versionCode by one for every release, including ones sent to F-Droid and APKPure. Current: versionCode 2, versionName 0.2.0. Tag the commit (for example git tag v0.2.0) and push the tag, because the F-Droid metadata builds from a tag.

## 4. GitHub Releases
Create a release for the tag on https://github.com/Ahmadns7/mercureal-assistant/releases, attach app-release.apk, and paste the changelog. Also attach the SHA-256 of the APK so users can verify it: sha256sum app-release.apk.

## 5. APKPure upload
Go to https://apkpure.com (developer / upload page), create a developer account, add the app with package id com.ahmadns7.mercureal.assistant, and upload app-release.apk. Fill in the listing: name MercuReal, short and full description (taken from the README), category (Productivity or Tools), screenshots, icon 512x512, the license MIT, source code link, and the privacy statement from README "Privacy and network use". Check the listing text against the README before publishing. APKPure reviews uploads; keep the signing key identical to GitHub releases.

## 6. Screenshots to take
Home screen with the listening state, an assistant answer on screen, the Dictate screen showing the transcript, Settings with the wake word and models, Notes or History. Use a device with no personal data on screen.

## 7. F-Droid request for packaging (RFP)
F-Droid does not accept direct uploads. You request packaging, and F-Droid's volunteers build it from source.
Steps:
a. Make sure the source builds with only the Gradle wrapper and the setup script (no closed dependencies). The Play Billing library is removed; check with grep -ri billing app/ that nothing references it.
b. Confirm the metadata draft at metadata/com.ahmadns7.mercureal.assistant.yml is current: set Builds commit to the release tag (v0.2.0), keep versionCode in step, and review the AntiFeatures and sherpa-onnx notes in the file.
c. Open an RFP issue at https://gitlab.com/fdroid/rfp/-/issues/new. Use the "Request for Packaging" template. Title: "RFP: MercuReal (com.ahmadns7.mercureal.assistant)". Include the app name, license (MIT), source URL https://github.com/Ahmadns7/mercureal-assistant, the package id, and a link to the metadata draft in the repo. Mention the prebuilt sherpa-onnx 2.0.0 AAR pinned by sha256 in scripts/setup_native.sh (Apache-2.0), and that the NDK 27.0.12077973 and CMake 3.22.1 are required.
d. Packagers may ask for a merge request to gitlab.com/fdroid/fdroiddata with the metadata file. They may ask you to fix build issues or move the AAR to a source build. Answer their questions in the issue thread. Do not rename the package id.
e. After merge, F-Droid builds, signs and publishes. Update the README Download section when it is live.

## 8. Things the code does not do for you
Nothing in CI uploads anywhere. APKPure listing text, icon, screenshots and the F-Droid RFP are entered by hand. The GitHub release and tag are created manually.
