# 🔑 ShieldKey — Vision « coffre à passkeys 100 % hors-ligne »

> Le pari : le mot de passe meurt (5 Md de passkeys en 2026, 90 % de notoriété). Au lieu de le subir,
> on en fait **l'argument phare** de ShieldKey. Une passkey est une **clé privée** — et une clé privée
> ne devrait JAMAIS quitter l'appareil. C'est exactement l'ADN de ShieldKey.

---

## 1. Ce qu'est réellement une passkey (pour cadrer)
Une passkey, c'est une **paire de clés** (FIDO2/WebAuthn) :
- une **clé publique** envoyée au site (il la garde) ;
- une **clé privée** qui reste sur ton appareil et **signe** un défi à chaque connexion.

Il n'y a **pas de secret partagé** transmissible (donc rien à hameçonner, rien à voler sur un serveur).
Se connecter = ton appareil signe un défi avec la clé privée. **Tout se joue localement.** C'est
précisément pour ça que ShieldKey est le candidat idéal : la clé privée peut vivre, chiffrée, dans le
coffre, et **ne jamais partir en ligne**.

## 2. Comment Android le permet : le Credential Manager
Depuis Android 14 (API 34), Google a unifié mots de passe + passkeys sous le **Credential Manager**.
Une appli tierce peut se déclarer **fournisseur d'identifiants** (`CredentialProviderService`) :
- **Création** (`BeginCreateCredentialRequest`) : un site demande de créer une passkey → ShieldKey
  génère la paire de clés, **stocke la clé privée chiffrée dans le coffre**, renvoie la clé publique.
- **Connexion** (`BeginGetCredentialRequest`) : le site présente un défi → ShieldKey, **après
  déverrouillage** (déverrouillage rapide déjà en place !), **signe** le défi avec la clé privée et
  renvoie la signature.
- Le même service gère aussi le **remplissage des mots de passe** classiques → un seul chantier couvre
  autofill + passkeys.

**Point clé** : rien n'oblige à synchroniser. Les autres (Google, 1Password…) synchronisent les
passkeys dans leur cloud. ShieldKey ferait **le contraire, volontairement** : passkeys **stockées
uniquement en local**, sauvegardables seulement via ton `.skb` chiffré.

## 3. L'argument de vente (unique sur le marché)
> **« Le seul coffre à passkeys 100 % hors-ligne. Tes clés de connexion nouvelle génération vivent
> sur ton téléphone, jamais sur un serveur. »**

Tous les concurrents vantent la **synchronisation** des passkeys (donc un cloud, donc une cible).
ShieldKey vante l'**inverse** : la souveraineté totale. C'est un positionnement que personne d'autre
ne peut tenir sans se renier.

## 4. Le CXP — entrer/sortir sans se faire enfermer
Le **Credential Exchange Protocol** (FIDO Alliance) permet de transférer passkeys ET mots de passe
entre gestionnaires, chiffré de bout en bout (Bitwarden 1er ; 1Password/Dashlane/Proton suivent).
- **Import CXP** = argument d'adoption : *« Viens depuis Bitwarden ou 1Password sans rien retaper. »*
- **Export CXP** = argument de confiance : *« Tu n'es pas prisonnier. Tu pars quand tu veux. »*
  (Paradoxalement, offrir la sortie **rassure** et fait rester.)

## 5. La roadmap réaliste (honnête sur la difficulté)
1. **Autofill classique d'abord** (`AutofillService`) — mots de passe + cartes sur les applis et les
   champs de navigateur coopérants. Base technique commune.
2. **Fournisseur de passkeys** (`CredentialProviderService`) — création + connexion, branchés sur le
   déverrouillage rapide et le son `inject` déjà présents.
3. **Import CXP** puis **export CXP**.

**⚠️ Le point dur honnête** (déjà noté dans REPRENDRE-DEMAIN) : les **pages de paiement en plein web**
dans Chrome résistent (Chrome privilégie son propre autofill, iframes/JS). Stratégie : viser d'abord
l'atteignable (applis + champs standard + passkeys via Credential Manager, qui, lui, est un canal
**officiel** et fiable), et rester clair sur les cas web qui résistent. Le **navigateur intégré**
(WebView + le `content.js` déjà écrit/testé sur Amazon) reste le plan B pour le paiement web.

## 6. Pourquoi ça vaut le coup malgré l'effort
- Ça fait passer ShieldKey de « énième coffre de mots de passe » à **« coffre du futur »**.
- C'est le **seul** différenciateur techno que les géants ne peuvent pas copier sans trahir leur modèle
  (leur valeur, c'est le cloud/sync ; la nôtre, c'est le hors-ligne).
- Ça aligne parfaitement le produit avec les 5 valeurs — surtout **sécurité** et **vision d'avenir**.
