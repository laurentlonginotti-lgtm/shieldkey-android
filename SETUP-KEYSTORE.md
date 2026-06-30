# 🔐 Keystore & secrets GitHub — ShieldKey

Le keystore est l'**identité officielle** de l'app : c'est lui qui « certifie » l'APK.
⚠️ **Si tu le perds, tu ne pourras plus publier de mise à jour.** Sauvegarde-le (et son mot de passe)
dans... ShieldKey 😉 / un endroit sûr. Ne le commite **jamais** (déjà protégé par `.gitignore`).

## 1. Générer le keystore (une seule fois)

Dans un terminal (le JDK fournit `keytool`). Adapte le mot de passe :

```bash
keytool -genkeypair -v \
  -keystore shieldkey-release.jks \
  -alias shieldkey \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=ShieldKey, OU=Dev, O=ShieldKey, C=FR" \
  -storepass TON_MOT_DE_PASSE -keypass TON_MOT_DE_PASSE
```

> PKCS12 partage le mot de passe du store et de la clé : mets **la même valeur** pour les deux.

## 2. Encoder le keystore en base64 (pour le secret GitHub)

```bash
# Git Bash / Linux / Mac
base64 -w0 shieldkey-release.jks > shieldkey-release.jks.base64
```
```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("shieldkey-release.jks")) > shieldkey-release.jks.base64
```

## 3. Créer les 4 secrets GitHub

Dépôt → **Settings → Secrets and variables → Actions → New repository secret** :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | tout le contenu de `shieldkey-release.jks.base64` (une seule ligne) |
| `KEYSTORE_PASSWORD` | le mot de passe choisi à l'étape 1 |
| `KEY_ALIAS` | `shieldkey` |
| `KEY_PASSWORD` | **la même valeur** que `KEYSTORE_PASSWORD` |

## 4. Vérifier la signature de l'APK release

```bash
apksigner verify --verbose app-release.apk
# doit indiquer : CN=ShieldKey ... et "Signature verifies"
```

## Note pour le Google Play Store

Si tu publies un jour sur le Play Store, active **Play App Signing** : tu fournis cette clé
comme « clé d'upload », Google gère la clé de distribution finale. Garde quand même CE keystore
précieusement (c'est ta clé d'upload).
