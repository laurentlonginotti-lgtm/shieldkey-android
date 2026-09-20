/*
 * ShieldKey — coffre-fort numérique 100 % hors-ligne
 * Copyright (C) 2026 Laurent Longinotti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shieldkey.vault.crypto

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.shieldkey.vault.util.AtomicWrite
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Déverrouillage RAPIDE (empreinte / visage OU code de l'écran) SANS affaiblir le zero-knowledge.
 *
 * Principe (chiffrement à enveloppe, comme la DEK) : une clé AES-256 est générée DANS l'enclave
 * matérielle (Android Keystore / StrongBox), utilisable seulement après une authentification.
 * Elle emballe la DEK du coffre ; seul le blob chiffré est écrit sur le disque interne privé.
 * Sans le matériel de CE téléphone ET l'authentification, le blob est inutile.
 *
 * DEUX MODES, et l'écart entre les deux est une vraie différence de sécurité :
 *
 *  • [Mode.STRONG] — biométrie forte uniquement, authentification exigée à CHAQUE usage de la clé
 *    (le Cipher passe par un [BiometricPrompt.CryptoObject]), et surtout
 *    `setInvalidatedByBiometricEnrollment` : si une empreinte est ajoutée ou retirée sur le
 *    téléphone, le système DÉTRUIT la clé. C'est ce qui ferme le scénario le plus réaliste —
 *    un proche qui connaît le code de l'écran enrôle son propre doigt et ouvre le coffre en
 *    silence. Ici cet enrôlement casse la clé et renvoie au mot de passe maître.
 *
 *  • [Mode.COMPAT] — biométrie OU code de l'écran, clé utilisable quelques secondes après une
 *    authentification de l'appareil. Indispensable pour les téléphones SANS capteur, mais plus
 *    faible sur deux points : la sécurité du coffre redescend à celle du verrouillage d'écran,
 *    et un nouvel enrôlement n'invalide PAS la clé (cette contrainte est incompatible avec
 *    l'usage du code de l'écran). Ce mode n'est retenu que si [Mode.STRONG] est impossible.
 *
 * Le mot de passe maître reste le seul secret que ShieldKey ne stocke nulle part ; le
 * déverrouillage rapide n'est qu'un raccourci local, révocable à tout moment.
 *
 * Format du blob `bio.skv` : `mode(1) || iv(12) || ciphertext||tag`.
 */
object BiometricGate {

    /** Le premier octet du blob dit comment le relire. */
    enum class Mode(val id: Byte) {
        STRONG(2),
        COMPAT(1);

        companion object {
            fun from(id: Byte): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    private const val ALIAS_STRONG = "shieldkey_bio_s1"
    private const val ALIAS_COMPAT = "shieldkey_bio_c1"
    /** Alias de la version précédente (mode unique) : nettoyé en même temps que les autres. */
    private const val LEGACY_ALIAS = "shieldkey_bio_v1"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12
    private const val AUTH_VALIDITY_SECONDS = 10

    private fun blobFile(context: Context) = File(context.filesDir, "bio.skv")

    // --- Disponibilité -------------------------------------------------------

    enum class BioStatus { READY, NO_LOCK }

    private fun hasStrongBiometric(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun hasDeviceCredential(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun status(context: Context): BioStatus =
        if (hasStrongBiometric(context) || hasDeviceCredential(context)) BioStatus.READY
        else BioStatus.NO_LOCK

    fun isAvailable(context: Context): Boolean = status(context) == BioStatus.READY

    /** Le meilleur mode possible sur CE téléphone. La biométrie forte est toujours préférée. */
    fun bestMode(context: Context): Mode =
        if (hasStrongBiometric(context)) Mode.STRONG else Mode.COMPAT

    /** Le déverrouillage rapide est activé (le blob emballé existe). */
    fun isEnabled(context: Context): Boolean = blobFile(context).exists()

    /** Mode réellement en vigueur, ou null si le déverrouillage rapide n'est pas activé. */
    fun activeMode(context: Context): Mode? = try {
        val b = blobFile(context).readBytes()
        if (b.size > IV_LEN + 1) Mode.from(b[0]) else null
    } catch (_: Exception) {
        null
    }

    /** Désactive : efface le blob emballé + toutes les clés matérielles connues. */
    fun disable(context: Context) {
        blobFile(context).delete()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            for (alias in listOf(ALIAS_STRONG, ALIAS_COMPAT, LEGACY_ALIAS)) {
                if (ks.containsAlias(alias)) ks.deleteEntry(alias)
            }
        } catch (_: Exception) { /* rien à nettoyer */ }
    }

    // --- Clé matérielle ------------------------------------------------------

    private fun aliasOf(mode: Mode) = if (mode == Mode.STRONG) ALIAS_STRONG else ALIAS_COMPAT

    private fun existingKey(mode: Mode): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(aliasOf(mode), null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) {
        null
    }

    private fun createKey(mode: Mode, strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            aliasOf(mode),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        if (mode == Mode.STRONG) {
            // Authentification exigée à chaque usage, biométrie forte seulement : c'est la
            // condition pour que le système accepte la contrainte d'invalidation ci-dessous.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            // Le cœur du durcissement : ajouter une empreinte détruit la clé.
            builder.setInvalidatedByBiometricEnrollment(true)
        } else {
            // Validité par durée : accepte AUSSI le code de l'écran, donc utilisable sur un
            // téléphone sans capteur. Déprécié depuis l'API 30 mais toujours fonctionnel, et
            // c'est la seule forme qui autorise les deux moyens d'authentification.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(builder.build()); generateKey()
            }
        } catch (e: Exception) {
            if (strongBox) createKey(mode, strongBox = false) else throw e
        }
    }

    // --- Activation ----------------------------------------------------------

    /**
     * Active le déverrouillage rapide : emballe [dek] avec une clé matérielle neuve.
     * [onResult] = true si activé, false si annulé / échec.
     */
    fun enable(
        activity: FragmentActivity,
        dek: ByteArray,
        title: String,
        subtitle: String,
        negative: String,
        onResult: (Boolean) -> Unit
    ) {
        val mode = bestMode(activity)
        // Une activation repart toujours d'une clé neuve : en cas de réactivation après une
        // invalidation, on ne veut pas réutiliser une clé dont on ignore l'état.
        disable(activity)

        val key = try {
            createKey(mode, strongBox = true)
        } catch (e: Exception) {
            onResult(false); return
        }

        val finish: (Cipher?) -> Unit = { authed ->
            if (authed == null) {
                onResult(false)
            } else {
                try {
                    val ct = authed.doFinal(dek)
                    AtomicWrite.write(blobFile(activity), byteArrayOf(mode.id) + authed.iv + ct)
                    onResult(true)
                } catch (e: Exception) {
                    onResult(false)
                }
            }
        }

        // L'ORDRE compte, et il diffère selon le mode :
        //  • STRONG : la clé exige une authentification à chaque usage, donc le Cipher doit être
        //    initialisé AVANT le prompt pour y être rattaché via le CryptoObject.
        //  • COMPAT : la clé n'est utilisable que dans les secondes qui suivent une
        //    authentification. Initialiser avant le prompt lèverait UserNotAuthenticatedException
        //    — il faut donc authentifier d'abord, puis initialiser.
        if (mode == Mode.STRONG) {
            val cipher = try {
                Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            } catch (e: Exception) {
                onResult(false); return
            }
            promptCrypto(activity, title, subtitle, negative, cipher, finish)
        } else {
            promptSimple(activity, title, subtitle) { ok ->
                finish(
                    if (!ok) null else try {
                        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
                    } catch (e: Exception) {
                        null
                    }
                )
            }
        }
    }

    // --- Déverrouillage ------------------------------------------------------

    /**
     * Déverrouille via l'authentification de l'appareil et renvoie la DEK.
     *
     * [onResult] reçoit `null` si l'utilisateur a annulé ou si l'opération a échoué, et
     * `invalidated = true` lorsque le système a détruit la clé (empreintes modifiées, ou
     * verrouillage d'écran retiré). Ce cas n'est PAS une erreur : c'est la protection qui a
     * joué, et l'appelant doit l'expliquer plutôt que d'afficher un échec muet.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negative: String,
        onResult: (dek: ByteArray?, invalidated: Boolean) -> Unit
    ) {
        val blob = try { blobFile(activity).readBytes() } catch (e: Exception) {
            onResult(null, false); return
        }
        if (blob.size <= IV_LEN + 1) { onResult(null, false); return }

        val mode = Mode.from(blob[0]) ?: run { disable(activity); onResult(null, true); return }
        val iv = blob.copyOfRange(1, 1 + IV_LEN)
        val ct = blob.copyOfRange(1 + IV_LEN, blob.size)

        val key = existingKey(mode) ?: run { disable(activity); onResult(null, true); return }

        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)

        val finish: (Cipher?) -> Unit = { authed ->
            if (authed == null) {
                onResult(null, false)
            } else {
                try {
                    onResult(authed.doFinal(ct), false)
                } catch (e: KeyPermanentlyInvalidatedException) {
                    disable(activity); onResult(null, true)
                } catch (e: Exception) {
                    onResult(null, false)
                }
            }
        }

        // Même distinction qu'à l'activation : STRONG initialise avant le prompt (CryptoObject),
        // COMPAT ne peut initialiser qu'une fois l'authentification obtenue.
        if (mode == Mode.STRONG) {
            val cipher = try {
                Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, spec) }
            } catch (e: KeyPermanentlyInvalidatedException) {
                // Une empreinte a été ajoutée ou retirée depuis l'activation : la clé est morte.
                disable(activity); onResult(null, true); return
            } catch (e: Exception) {
                onResult(null, false); return
            }
            promptCrypto(activity, title, subtitle, negative, cipher, finish)
        } else {
            promptSimple(activity, title, subtitle) { ok ->
                if (!ok) {
                    finish(null)
                } else {
                    try {
                        finish(Cipher.getInstance(TRANSFORMATION).apply {
                            init(Cipher.DECRYPT_MODE, key, spec)
                        })
                    } catch (e: KeyPermanentlyInvalidatedException) {
                        // COMPAT : survient quand le verrouillage d'écran a été retiré.
                        disable(activity); onResult(null, true)
                    } catch (e: Exception) {
                        onResult(null, false)
                    }
                }
            }
        }
    }

    // --- Prompts système ------------------------------------------------------

    private fun callback(onDone: (BiometricPrompt.AuthenticationResult?) -> Unit) =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onDone(null)
            override fun onAuthenticationFailed() { /* mauvaise empreinte : le système laisse réessayer */ }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onDone(result)
        }

    /** Mode STRONG : le Cipher est lié à l'authentification et revient prêt à l'emploi. */
    private fun promptCrypto(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negative: String,
        cipher: Cipher,
        onResult: (Cipher?) -> Unit
    ) {
        // Biométrie seule → le système ne propose pas « Utiliser le code », il faut donc
        // fournir nous-mêmes un libellé de bouton négatif, sinon le prompt refuse de s'ouvrir.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negative)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback { result ->
            onResult(if (result == null) null else result.cryptoObject?.cipher ?: cipher)
        }).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Mode COMPAT : biométrie OU code de l'écran, la clé reste utilisable quelques secondes. */
    private fun promptSimple(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit
    ) {
        // DEVICE_CREDENTIAL autorisé → PAS de bouton négatif (le système fournit « Utiliser le code »).
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback { result ->
            onResult(result != null)
        }).authenticate(info)
    }
}
