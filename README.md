# 🛡️ ShieldKey — le coffre-fort qui ne parle à personne

Mots de passe, moyens de paiement, seed crypto et documents : chiffrés sur **ton** téléphone,
et qui **ne partent jamais en ligne**. Pas de compte, pas de serveur, pas d'abonnement.
Application native Android (Kotlin + Jetpack Compose). Conçu en France 🇫🇷.

---

## Pourquoi ce code est public

Un coffre-fort ne mérite ta confiance que si tu peux **vérifier** ce qu'il fait.
Alors on ne te demande pas de nous croire : **lis le code, compile-le toi-même.**

- 🔌 **Zéro permission Internet** — vérifiable dans [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
- 🔒 **AES-256-GCM + Argon2id** — paramètres exacts dans [`SECURITE.md`](SECURITE.md).
- 🧩 **Builds reproductibles** — l'APK que tu compiles correspond à celui du Play Store.
- 🚫 **Aucun tracker, aucune pub, aucun SDK tiers.**

## Gratuit vs Play Store (modèle open-core)

Ce dépôt te permet de **compiler et d'utiliser ShieldKey gratuitement**.
La version du **Play Store** est le même logiciel, mais **signé, mis à jour automatiquement et
maintenu** — l'acheter (achat unique, sans abonnement), c'est le confort et le soutien au projet.

## Les 5 valeurs

| Valeur | Promesse | Preuve |
|---|---|---|
| 🤝 Confiance | « Ne me crois pas, vérifie » | Code ouvert + zéro permission Internet |
| 🛋️ Confort | Simple, en français, tout au même endroit | Déverrouillage rapide, design soigné |
| 🪂 Assurance | Tu ne peux rien perdre | Kit de secours hors-ligne + sauvegarde `.skb` |
| 🔒 Sécurité | Niveau bancaire, sur ton appareil | AES-256-GCM + Argon2id, puce sécurisée (TEE) |
| 🔭 Vision d'avenir | Prêt pour l'après-mot-de-passe | Passkeys hors-ligne (à venir), paiements modernes |

## Fonctionnalités

- 🔑 Connexions · 💳 Cartes / IBAN (avec **genres** : physique, e-carte, Click to Pay, paiement mobile, IBAN)
  · ₿ Crypto (seed / clés / exchanges) · 📝 Notes · 📄 Documents chiffrés (hors-ligne)
- 🔓 Déverrouillage rapide (empreinte ou code de l'appareil), verrouillage auto à la veille
- 🆘 Kit de secours hors-ligne (récupération sans serveur ni porte dérobée)
- 🔑 Changement du mot de passe maître (code de secours conservé, ou renouvelé au choix) ; alerte si le mot de passe est trop court
- 💾 Sauvegarde / restauration chiffrée `.skb` (changement de téléphone)
- 📋 Presse-papier durci (masquage Android 13+, effacement automatique)
- 🌐 Interface FR / EN
- ⏳ *À venir* : remplissage auto + **fournisseur de passkeys 100 % hors-ligne** (voir [`PASSKEYS-VISION.md`](PASSKEYS-VISION.md))

## Construire l'APK (build cloud, sans SDK local)

1. Configurer le keystore + les secrets → voir [`SETUP-KEYSTORE.md`](SETUP-KEYSTORE.md).
2. Onglet **Actions** : le workflow « Build ShieldKey APK » produit `ShieldKey-debug` (toujours)
   et `ShieldKey-release` (signé, si les secrets sont en place).
3. Télécharger l'artifact et l'installer sur le téléphone.

Environnement figé (pour la reproductibilité) : AGP 8.2.0 · Kotlin 1.9.22 · Compose compiler 1.5.8 · compileSdk 34 · minSdk 26 · JDK 17.

## Confidentialité

ShieldKey ne collecte **aucune** donnée. Voir [`POLITIQUE-CONFIDENTIALITE.md`](POLITIQUE-CONFIDENTIALITE.md).

## Contact

- Questions & support : **shieldkey.app@gmail.com**
- 🛡️ **Signaler une faille de sécurité** : écris à la même adresse avec l'objet « SECURITE ».
  Merci de nous laisser un délai raisonnable pour corriger avant toute divulgation publique
  (divulgation responsable).

## Licence

Code sous **GNU General Public License v3.0** (voir [`LICENSE`](LICENSE)).
Copyright © 2026 Laurent Longinotti.

Le nom **« ShieldKey »** et le logo sont des **marques** de l'auteur : une version recompilée à partir
de ce code doit être **renommée** et ne peut pas utiliser l'icône officielle.
