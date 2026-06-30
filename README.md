# 🛡️ ShieldKey — coffre-fort de mots de passe (Android)

Gestionnaire de mots de passe **100 % hors-ligne**, chiffré, avec stockage de cartes (Euro/IBAN)
et de crypto (phrases de récupération, clés). Application native Android (Kotlin + Jetpack Compose).

## Décisions d'architecture (verrouillées)

| Sujet | Choix |
|---|---|
| Chiffrement | **AES-256-GCM** + **Argon2id** (dérivation du mot de passe maître) |
| Récupération | **Kit de secours hors-ligne** (code de récupération) — *pas de serveur, pas de porte dérobée* |
| Données | Connexions · Cartes/Euro/IBAN · Crypto (phrases/clés/exchanges) · Notes |
| Remplissage auto | Service Android natif (mots de passe **et** carte) — *à venir* |
| Réseau | **Aucune permission Internet** (argument de confiance) |
| Couleurs | Émeraude Nuit (thème Violet en alternatif prévu) |
| Build / signature | **Cloud GitHub Actions** + keystore `CN=ShieldKey` |

## État d'avancement

- [x] **1. Fondations** — projet Gradle, thème Émeraude, icône, écran de verrouillage de démo, CI signée
- [ ] 2. Cœur crypto (Argon2id + AES-GCM) + coffre chiffré local
- [ ] 3. Création de coffre + kit de secours
- [ ] 4. Coffre : liste / détail / ajout (Euro / Crypto / Connexions / Notes)
- [ ] 5. Service de remplissage auto (paiement + mots de passe)
- [ ] 6. Générateur + audit + thème Violet + finitions
- [ ] 7. Fiche Play + politique de confidentialité

## Construire l'APK (build cloud)

1. Créer un dépôt GitHub privé et y pousser ce dossier.
2. Générer le keystore + configurer les 4 secrets → voir **SETUP-KEYSTORE.md**.
3. Onglet **Actions** : le workflow « Build ShieldKey APK » produit :
   - `ShieldKey-debug` (toujours)
   - `ShieldKey-release` (signé, si les secrets sont en place)
4. Télécharger l'artifact, installer l'APK sur le téléphone.

> Aperçus design : `APERCU-DESIGN.html` (écrans) et `ICONE-DESIGN.html` (icône) — à ouvrir dans un navigateur.
