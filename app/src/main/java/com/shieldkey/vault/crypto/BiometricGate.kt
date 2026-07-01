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
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Déverrouillage RAPIDE (empreinte / visage OU code de l'écran) SANS affaiblir le zero-knowledge.
 *
 * Principe (chiffrement à enveloppe, comme la DEK) :
 *  - Une clé AES-256 est générée DANS l'enclave matérielle (Android Keystore / StrongBox),
 *    utilisable uniquement APRÈS une authentification de l'appareil (biométrie forte OU
 *    code de verrouillage : PIN / schéma / mot de passe). Cette clé ne quitte jamais le matériel.
 *  - Elle emballe la DEK du coffre ; seul le blob chiffré (iv || ciphertext) est écrit sur le
 *    disque interne privé (bio.skv). Sans le matériel de CE téléphone + l'auth, le blob est inutile.
 *
 * Authentification liée à la durée (setUserAuthenticationValidityDurationSeconds / Parameters) :
 * après une auth réussie via BiometricPrompt (empreinte OU code), la clé est utilisable
 * quelques secondes → on chiffre/déchiffre immédiatement. Compatible dès Android 8 (API 26),
 * y compris pour les téléphones SANS capteur biométrique (ils utilisent le code de l'écran).
 *
 * Le mot de passe maître reste le rempart ultime ; ceci n'est qu'un raccourci local.
 */
object BiometricGate {

    private const val KEY_ALIAS = "shieldkey_bio_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12
    private const val AUTH_VALIDITY_SECONDS = 10

    private val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun blobFile(context: Context) = File(context.filesDir, "bio.skv")

    /** État du déverrouillage rapide : READY si une auth d'appareil existe (biométrie OU code). */
    enum class BioStatus { READY, NO_LOCK }

    fun status(context: Context): BioStatus {
        val bm = BiometricManager.from(context)
        val strongBiometric =
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        val deviceSecure =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return if (strongBiometric || deviceSecure) BioStatus.READY else BioStatus.NO_LOCK
    }

    /** Une auth d'appareil (empreinte OU code de l'écran) est disponible. */
    fun isAvailable(context: Context): Boolean = status(context) == BioStatus.READY

    /** L'utilisateur a activé le déverrouillage rapide (le blob emballé existe). */
    fun isEnabled(context: Context): Boolean = blobFile(context).exists()

    /** Désactive : efface le blob emballé + la clé matérielle. */
    fun disable(context: Context) {
        blobFile(context).delete()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) { /* rien à nettoyer */ }
    }

    // --- Clé matérielle ------------------------------------------------------

    private fun existingKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) { null }

    private fun createKey(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        // Auth valable quelques secondes après un déverrouillage de l'appareil (biométrie OU code
        // de l'écran). setUserAuthenticationValidityDurationSeconds marche dès Android 6 (API 23) et
        // autorise les DEUX modes ; on l'utilise partout (déprécié en API 30 mais toujours
        // fonctionnel) pour éviter les constantes KeyProperties.AUTH_* introuvables à la compilation.
        @Suppress("DEPRECATION")
        builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(builder.build()); generateKey()
            }
        } catch (e: Exception) {
            if (strongBox) createKey(strongBox = false) else throw e
        }
    }

    // --- Activation ----------------------------------------------------------

    /**
     * Active le déverrouillage rapide : demande l'auth, puis emballe [dek] avec la clé matérielle.
     * [onResult] = true si activé, false si annulé / échec.
     */
    fun enable(
        activity: FragmentActivity,
        dek: ByteArray,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit
    ) {
        val key = try {
            existingKey() ?: createKey(strongBox = true)
        } catch (e: Exception) {
            onResult(false); return
        }
        prompt(activity, title, subtitle) { authed ->
            if (!authed) { onResult(false); return@prompt }
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val ct = cipher.doFinal(dek)
                blobFile(activity).writeBytes(cipher.iv + ct)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // --- Déverrouillage ------------------------------------------------------

    /**
     * Déverrouille via l'auth d'appareil : demande empreinte/code, puis renvoie la DEK déchiffrée.
     * [onResult] = la DEK, ou null si annulé / échec.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (ByteArray?) -> Unit
    ) {
        val blob = try { blobFile(activity).readBytes() } catch (e: Exception) { onResult(null); return }
        if (blob.size <= IV_LEN) { onResult(null); return }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)

        val key = existingKey() ?: run { onResult(null); return }

        prompt(activity, title, subtitle) { authed ->
            if (!authed) { onResult(null); return@prompt }
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                onResult(cipher.doFinal(ct))
            } catch (e: KeyPermanentlyInvalidatedException) {
                // Verrouillage d'écran retiré depuis l'activation → on invalide, retour au mot de passe.
                disable(activity); onResult(null)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // --- Prompt système commun ----------------------------------------------

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            override fun onAuthenticationFailed() { /* mauvaise empreinte : le système laisse réessayer */ }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
        }
        // DEVICE_CREDENTIAL autorisé → PAS de bouton négatif (le système fournit « Utiliser le code »).
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info)
    }
}
