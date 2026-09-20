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

package com.shieldkey.vault.util

import kotlin.math.ln

/**
 * Estimation de la force du mot de passe maître — hors-ligne, sans dépendance, sans réseau.
 *
 * POURQUOI c'est le point le plus important de la sécurité de ShieldKey : le fichier de
 * sauvegarde `.skb` sort volontairement du téléphone (Drive, ordinateur, clé USB). Qui met la
 * main dessus attaque hors ligne, sans limite de vitesse, et n'a plus qu'Argon2id devant lui.
 * Argon2id rend chaque essai coûteux (~1 000 essais/s sur un GPU haut de gamme), mais il ne
 * rattrape PAS un mot de passe devinable : 8 caractères choisis par un humain (~28 bits) tombent
 * en quelques heures, là où 12 caractères variés tiennent des années.
 *
 * Ce que l'estimation vaut, honnêtement : elle mesure la *composition* (longueur × variété), en
 * retirant ce qui est visiblement prévisible (répétitions, suites, motifs très courants). Elle ne
 * sait pas qu'un mot de passe est un prénom suivi d'une date — un vrai estimateur (zxcvbn) le
 * verrait. Elle SURESTIME donc un mot de passe « humain » : les seuils ci-dessous sont volontairement
 * exigeants pour compenser, et le conseil affiché pousse vers la phrase de passe, qui est la seule
 * façon simple d'atteindre une vraie résistance.
 */
object PasswordStrength {

    /** Longueur minimale acceptée à la création. En dessous, Argon2id ne suffit plus. */
    const val MIN_LENGTH = 12

    enum class Level { WEAK, FAIR, GOOD, STRONG }

    /** Seuils en bits estimés (après retrait du prévisible). */
    private const val BITS_FAIR = 45
    private const val BITS_GOOD = 60
    private const val BITS_STRONG = 75

    /** Motifs qu'un attaquant essaie en premier : présents = le mot de passe ne vaut rien. */
    private val COMMON = listOf(
        "password", "motdepasse", "azerty", "qwerty", "123456", "111111",
        "shieldkey", "admin", "iloveyou", "soleil", "bonjour", "welcome"
    )

    /**
     * Taille de l'alphabet que l'attaquant doit parcourir, déduite des familles réellement
     * utilisées. Utiliser une seule famille (« que des minuscules ») divise l'espace de recherche.
     */
    private fun alphabetSize(pwd: String): Int {
        var size = 0
        if (pwd.any { it in 'a'..'z' }) size += 26
        if (pwd.any { it in 'A'..'Z' }) size += 26
        if (pwd.any { it in '0'..'9' }) size += 10
        if (pwd.any { !it.isLetterOrDigit() }) size += 33
        return size.coerceAtLeast(1)
    }

    /**
     * Longueur « utile » : un caractère qui prolonge une répétition (aaa) ou une suite (abc, 987)
     * n'ajoute presque rien, puisqu'une attaque par dictionnaire les enchaîne d'office.
     */
    private fun effectiveLength(pwd: String): Double {
        if (pwd.isEmpty()) return 0.0
        var eff = 1.0
        for (i in 1 until pwd.length) {
            val prev = pwd[i - 1].code
            val cur = pwd[i].code
            val predictable = cur == prev || cur - prev == 1 || prev - cur == 1
            eff += if (predictable) 0.4 else 1.0
        }
        return eff
    }

    /** Entropie estimée, en bits. */
    fun estimateBits(pwd: String): Int {
        if (pwd.isEmpty()) return 0
        val lower = pwd.lowercase()
        // Un motif ultra-courant rend le reste cosmétique : on plafonne franchement.
        val common = COMMON.any { lower.contains(it) }
        val bits = effectiveLength(pwd) * (ln(alphabetSize(pwd).toDouble()) / ln(2.0))
        val penalised = if (common) bits.coerceAtMost(20.0) else bits
        return penalised.toInt()
    }

    fun level(pwd: String): Level {
        val bits = estimateBits(pwd)
        return when {
            bits < BITS_FAIR -> Level.WEAK
            bits < BITS_GOOD -> Level.FAIR
            bits < BITS_STRONG -> Level.GOOD
            else -> Level.STRONG
        }
    }

    /** Part de la jauge à remplir (0f..1f), bornée au seuil « excellent ». */
    fun fraction(pwd: String): Float =
        (estimateBits(pwd).toFloat() / BITS_STRONG).coerceIn(0f, 1f)
}
