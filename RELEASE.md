# MercuReal release guide

## 1. Signing (once, never commit the keystore)
Create a keystore: keytool -genkeypair -v -keystore mercureal-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mercureal
Then either export env vars MERCUREAL_KEYSTORE_PATH, MERCUREAL_KEYSTORE_PASSWORD, MERCUREAL_KEY_ALIAS, MERCUREAL_KEY_PASSWORD, or create keystore.properties at the repo root with keys storeFile, storePassword, keyAlias, keyPassword. Both are git-ignored. Without them the release build falls back to debug signing (fine for CI, not for Play).
Build: ./gradlew :app:bundleRelease  (produces app/build/outputs/bundle/release/app-release.aab; upload this to Play, not the APK).
Keep the keystore and passwords backed up. Play App Signing is recommended: Google holds the app signing key, you keep an upload key.

## 2. Versioning
versionCode and versionName live in app/build.gradle.kts. Bump versionCode by one for every upload. Current: versionCode 2, versionName 0.2.0.

## 3. Play Console: create the app
Create app, name MercuReal, default language English (or your choice), app or game: App, free. Accept developer policies. Complete the store listing: short description (80 chars max), full description, app icon (512x512 PNG), feature graphic (1024x500), phone screenshots (at least 2, 16:9 or 9:16, 1080px+ wide).

## 4. Screenshots to take
Home screen with the "ahmad" listening state, an assistant answer on screen, the Dictate screen showing the transcript and share button, Settings with the wake word and models, the MercuReal Pro screen, Notes or History. Take them on a real device, showing no personal data.

## 5. Content rating
Play Console > Policy > App content > Content rating: start the IARC questionnaire. Answer no to violence, sexual content, profanity features, gambling, and user-generated content shared with others. Expected result: Everyone / PEGI 3.

## 6. Data safety form (answers for this offline app)
Does the app collect or share user data: No. The app works offline, has no account, and no analytics or ad SDK. Microphone audio is processed on the device and is not collected or transmitted. Permission INTERNET is used only to download the on-device model files when the user chooses; no user data is sent. Data is not encrypted in transit because no user data is transmitted. Users can request data deletion by clearing app data or uninstalling. Declare the in-app purchase under Purchases, handled by Google Play Billing (purchase token stored by Google). Mark the app as not requiring a privacy policy only if your Play account rules allow; otherwise publish a short privacy policy stating the points above.

## 7. Monetization: Pro in-app product
Play Console > Monetize > Products > In-app products > Create product. Product ID: mercureal_pro (must match billing/BillingConfig.kt PRODUCT_ID exactly). Name: MercuReal Pro. Price: your choice. Activate it. Until it is active and the app is on a testing track, purchases show "not available" and the free tier applies.
Free tier cap: FreeTier.DAILY_CONVERSATIONS in model/Entitlements.kt (default 20 conversations per local day).

## 8. Testing and rollout
Upload the .aab to Internal testing first. Add testers by email, test a purchase with a license tester account (Setup > License testing), then promote to Production. Review can take a few days for the first release.

## 9. Things the code does not do for you
Nothing in CI uploads to Play. Store listing text, icon, screenshots, rating and data safety answers are entered manually in Play Console.
