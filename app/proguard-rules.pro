# Minification (R8) désactivée pour l'instant : voir app/build.gradle (minifyEnabled false).
# À compléter quand on activera R8 — notamment garder BouncyCastle (Argon2) :
# -keep class org.bouncycastle.** { *; }
# -dontwarn org.bouncycastle.**
