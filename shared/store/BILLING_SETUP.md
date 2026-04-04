# Billing Setup — My Lift Squad

## Overview

The app uses Google Play Billing (billing-ktx 7.1.1) for a single one-time in-app purchase.

- **Product ID:** `support_developer`
- **Type:** One-time (non-consumable)
- **Purpose:** Optional donation to support the developer. Unlocks motivational quotes on the home screen and removes the "Support Developer" option from the speed dial.

---

## Step 1: Create a Google Play Developer Account

If you don't already have one:
1. Go to https://play.google.com/console
2. Sign in with your Google account
3. Pay the one-time $25 registration fee
4. Complete identity verification

---

## Step 2: Create the App in Play Console

1. Go to **All apps → Create app**
2. App name: `My Lift Squad`
3. Default language: English (United Kingdom) or English (United States)
4. App or Game: **App**
5. Free or Paid: **Free**
6. Accept the declarations and click **Create app**

---

## Step 3: Create the In-App Product

1. In the left sidebar go to **Monetise → In-app products**
2. Click **Create product**
3. Fill in:
   - **Product ID:** `support_developer` ← must match exactly
   - **Name:** Support the Developer
   - **Description:** A one-time purchase to support the development of My Lift Squad. Thank you!
   - **Status:** Active
4. Under **Pricing**, click **Set price** and choose your price (e.g. €0.99 / $0.99)
5. Click **Save**

> ⚠️ The product ID `support_developer` is hardcoded in `BillingManager.kt`. Do not change it.

---

## Step 4: Set Up App Signing

Google Play requires your app to be signed.

### Generate a keystore (one-time, keep this safe forever):
```bash
keytool -genkey -v \
  -keystore ~/keystores/myliftsquad.jks \
  -alias myliftsquad \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Store the keystore file and passwords somewhere safe (password manager). **If you lose this, you cannot update the app.**

### Configure signing in `android/app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("/path/to/myliftsquad.jks")
            storePassword = "your_store_password"
            keyAlias = "myliftsquad"
            keyPassword = "your_key_password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Build release APK / AAB:
```bash
cd android
./gradlew bundleRelease   # produces .aab (preferred for Play Store)
# or
./gradlew assembleRelease # produces .apk
```

Output: `app/build/outputs/bundle/release/app-release.aab`

---

## Step 5: Upload to Play Console

1. Go to **Release → Production → Create new release**
2. Upload the `.aab` file
3. Fill in release notes
4. Roll out

---

## Step 6: Test Billing Before Publishing

To test billing without real money:

### Option A: Debug flag (UI only)
In `BillingManager.kt`, set:
```kotlin
const val DEBUG_FORCE_DONATED = true
```
This bypasses the billing check entirely and shows the donated state.

### Option B: Google Play License Testers
1. In Play Console go to **Setup → License testing**
2. Add your Google account as a licence tester
3. Any purchase made with that account will be free and can be cancelled/refunded instantly

### Option C: Internal testing track
1. Upload the app to the **Internal testing** track
2. Add yourself as a tester
3. Install from the Play Store link (not sideloaded)
4. Purchases on internal testing tracks are free for licence testers

> ⚠️ Billing only works in builds installed from the Play Store (or internal testing). It will not work in sideloaded/debug builds with a real product.

---

## Checklist Before Submitting

- [ ] `support_developer` product created and set to **Active** in Play Console
- [ ] `DEBUG_FORCE_DONATED` is set back to `false` in `BillingManager.kt`
- [ ] App signed with release keystore
- [ ] Privacy policy URL live at https://gooseyirl.github.io/my-lift-squad/
- [ ] `BILLING` permission in AndroidManifest.xml ✅ (already added)
- [ ] Store listing complete (title, description, screenshots, feature graphic)
- [ ] Content rating questionnaire completed in Play Console
- [ ] Target audience set to Adults (18+)
