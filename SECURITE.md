# 🔒 ShieldKey — Livre blanc de sécurité

> Transparence totale sur la façon dont ShieldKey protège tes données. Rien n'est caché : c'est le
> principe même d'un coffre-fort digne de confiance. Ce document décrit le modèle de sécurité réel de
> l'application (code vérifiable dans ce dépôt).

## Principe fondateur : 100 % hors-ligne

ShieldKey ne demande **aucune permission Internet**. Techniquement, l'application **ne peut rien
envoyer** vers l'extérieur. Il n'y a **aucun serveur**, **aucun compte**, **aucun cloud**. Tes secrets
vivent chiffrés sur ton appareil, et nulle part ailleurs. On ne peut pas pirater un serveur qui
n'existe pas.

## Dérivation de clé : Argon2id

Ton mot de passe maître n'est **jamais stocké**. Il sert à dériver une clé via **Argon2id**, le
vainqueur de la Password Hashing Competition, conçu pour être **coûteux en mémoire** :

| Paramètre | Valeur |
|---|---|
| Algorithme | Argon2id |
| Passes (itérations) | 3 |
| Mémoire | 64 Mo |
| Parallélisme | 2 |

Le coût mémoire rend les attaques par **GPU/ASIC** (matériel de force brute massif) beaucoup plus
chères qu'avec un simple PBKDF2 : chaque essai consomme 64 Mo de RAM.

## Chiffrement : AES-256-GCM

Les données sont chiffrées avec **AES-256 en mode GCM** (chiffrement **authentifié**) :

| Paramètre | Valeur |
|---|---|
| Algorithme | AES-256-GCM |
| Taille de clé | 256 bits |
| Tag d'authentification | 128 bits |

Le mode GCM garantit non seulement la **confidentialité** mais aussi l'**intégrité** : toute
altération du coffre est détectée au déchiffrement.

## Modèle d'enveloppe (zéro-knowledge)

ShieldKey n'utilise pas directement ton mot de passe pour chiffrer chaque entrée. Il emploie un
**modèle d'enveloppe** :

1. Une **clé de chiffrement des données (DEK)** de 256 bits chiffre le contenu du coffre.
2. Cette DEK est elle-même **emballée** (chiffrée) séparément par :
   - ta **clé dérivée du mot de passe maître**, et
   - ta **clé dérivée du code de secours** (kit de récupération).

Conséquence : changer de mot de passe ne réchiffre pas tout le coffre (on ne rechiffre que la DEK).
Et **perdre le mot de passe maître** n'est pas fatal : le **kit de secours** peut débloquer la même DEK.
Nous, éditeur, n'avons **aucune** de ces clés — récupération impossible de notre côté (pas de porte dérobée).

## Déverrouillage rapide : ancré dans le matériel

Le déverrouillage par empreinte ou par code de l'appareil s'appuie sur une clé **AES-256 du Keystore
Android / de l'élément sécurisé (TEE/StrongBox)**, liée à ton authentification. Cette clé matérielle
**ne quitte jamais** la puce sécurisée. Le mot de passe maître reste le rempart de fond ; le
déverrouillage rapide est un confort protégé par le matériel.

## Presse-papier durci

Quand tu copies un champ sensible (mot de passe, CVV, IBAN, seed, clé privée), ShieldKey :
- marque la copie `EXTRA_IS_SENSITIVE` (masquée dans l'aperçu/historique/suggestions sur Android 13+),
- l'**efface automatiquement après 45 secondes**,
- l'**efface au verrouillage** du coffre.

## Autres protections

- **Superpositions bloquées** (Android 12+, `setHideOverlayWindows`) : tant que ShieldKey est à l'écran,
  aucune autre appli ne peut dessiner par-dessus — ni faux champ « mot de passe », ni bouton invisible
  sous le doigt (la méthode favorite des chevaux de Troie bancaires).
- **Téléphone rooté** : ShieldKey le détecte (binaire `su`, gestionnaires de root connus) et l'annonce,
  sans jamais bloquer. Le root abolit le cloisonnement d'Android ; le chiffrement tient toujours, mais
  une appli à qui tu accordes le root peut lire la mémoire pendant que le coffre est ouvert. Cette
  détection se contourne (Magisk DenyList) : c'est un avertissement, pas une garantie.
- **Coffre endommagé ≠ mauvais mot de passe** : un fichier altéré (support défaillant, rançongiciel sur
  téléphone rooté) est diagnostiqué comme tel, et l'appli renvoie vers la restauration d'une sauvegarde
  au lieu de laisser croire à une erreur de frappe.
- **Rappel de sauvegarde** : jamais sauvegardé, sauvegarde de plus de 30 jours avec des modifications,
  ou mot de passe changé depuis — le coffre le signale. Contre la perte, le vol et les rançongiciels,
  c'est la sauvegarde `.skb` hors du téléphone qui protège, rien d'autre.
- `allowBackup=false` : le système Android ne sauvegarde pas automatiquement le coffre.
- Les **documents** sont chiffrés séparément (un fichier `.blob` par document, AES-256-GCM avec la DEK).
- Les **sauvegardes `.skb`** sont rechiffrées (Argon2id + AES-256-GCM) avec ton mot de passe maître.
  Une sauvegarde déjà exportée reste protégée par le mot de passe en vigueur au moment de l'export :
  après un changement de mot de passe, refais-en une.

## Résistance quantique

Le coffre repose sur de la cryptographie **symétrique** (AES-256). Face à un futur ordinateur quantique,
l'algorithme de Grover ne réduit la sécurité effective qu'à **128 bits** — ce qui reste hors de portée.
Le danger quantique réel concerne la cryptographie **asymétrique** (RSA/ECC), que le coffre de ShieldKey
**n'utilise pas** pour chiffrer tes données. En clair : **AES-256 est déjà prêt pour l'ère quantique.**

## Vérifie par toi-même

- Les permissions : [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — cherche `INTERNET`, tu ne la trouveras pas.
- Le cœur crypto : classes `SkCrypto`, `VaultStore`, `BiometricGate`, `BackupManager`.
- Compile depuis la source et compare l'empreinte SHA-256 de l'APK avec celle du Play Store.
