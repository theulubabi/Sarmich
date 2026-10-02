# Usar Browser does not expose any JavaScript interfaces, so no @JavascriptInterface keep rules are needed.
# Room, DataStore, Compose and ZXing ship their own consumer rules.
-dontwarn org.slf4j.**

# Credential Manager (Google sign-in)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
