# Usar

Android brauzer: Kotlin, Jetpack Compose (Material 3), Room, DataStore, Coroutines, MVVM, Android WebView.

## APK yig‘ish
1. Android Studio (Koala yoki yangiroq) da **File → Open** orqali shu papkani oching.
2. Gradle sinxronlanishini kuting (JDK 17).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   APK: `app/build/outputs/apk/debug/app-debug.apk`

Buyruq satridan: `gradle wrapper` (bir marta), so‘ng `./gradlew assembleDebug`.

Release build hozircha debug kalit bilan imzolanadi — Play Store uchun o‘z kalitingizni `app/build.gradle.kts` da sozlang.

## Tuzilma
- `browser/` — WebView dvigateli, xavfsizlik, yuklamalar, maxfiy profil, tashqi ilovalar
- `data/` — Room bazasi, sozlamalar, zaxira (JSON), favikon keshi
- `ui/` — Compose ekranlari (brauzer, saqlangan saytlar, tarix, yuklamalar, sozlamalar, onboarding)
- `res/values`, `values-uz`, `values-ru` — inglizcha, o‘zbekcha, ruscha matnlar

## Google orqali kirishni yoqish
Ilova birinchi ochilganda "Google bilan kirish" yoki "Mehmon sifatida davom etish" so‘raladi.
Mehmon rejimi hech qanday sozlashsiz ishlaydi. Google orqali kirish uchun bir marta:

1. https://console.cloud.google.com da loyiha yarating, **OAuth consent screen** ni to‘ldiring.
2. **Credentials → Create credentials → OAuth client ID**:
   - **Android** turi: paket `uz.usar.browser` va SHA-1 barmoq izi
     (`./gradlew signingReport` yoki Android Studio → Gradle → signingReport).
   - **Web application** turi ham yarating — uning Client ID si kerak bo‘ladi.
3. `gradle.properties` da: `usar.googleWebClientId=XXXX.apps.googleusercontent.com` (Web client ID).
4. Loyihani qayta yig‘ing.

Usar faqat ism va e-pochtani qurilmada saqlaydi. Parol va ID token saqlanmaydi.
Google hisobi yo‘q bo‘lsa, "Yangi hisob yaratish" tugmasi Androidning hisob qo‘shish oynasini ochadi.
