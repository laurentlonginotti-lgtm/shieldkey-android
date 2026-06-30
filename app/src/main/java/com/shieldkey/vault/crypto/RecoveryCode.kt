package com.shieldkey.vault.crypto

/**
 * Kit de secours hors-ligne : un code lisible que l'utilisateur note/imprime.
 * Format : 7 groupes de 5 caractères (alphabet sans I, L, O, U pour éviter les confusions).
 * ~175 bits d'entropie → impossible à deviner.
 */
object RecoveryCode {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val GROUPS = 7
    private const val GROUP_LEN = 5

    /** Génère un nouveau code de secours, ex. "7H2KQ-9MX4T-...". */
    fun generate(): String {
        val raw = SkCrypto.randomBytes(GROUPS * GROUP_LEN)
        val sb = StringBuilder()
        var i = 0
        for (g in 0 until GROUPS) {
            if (g > 0) sb.append('-')
            repeat(GROUP_LEN) {
                sb.append(ALPHABET[(raw[i].toInt() and 0xFF) % ALPHABET.length])
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Normalise la saisie de l'utilisateur (majuscules, retire tirets/espaces,
     * corrige les confusions visuelles I/L→1 et O→0) pour la dérivation.
     */
    fun normalize(input: String): ByteArray {
        val s = input.uppercase()
            .replace("-", "")
            .replace(" ", "")
            .replace('I', '1')
            .replace('L', '1')
            .replace('O', '0')
        return s.toByteArray(Charsets.US_ASCII)
    }
}
