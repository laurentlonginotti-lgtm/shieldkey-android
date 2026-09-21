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

import com.shieldkey.vault.util.PasswordStrength.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'estimateur est volontairement sévère (voir son en-tête). On vérifie surtout qu'il ne
 * flatte jamais ce qu'un attaquant essaie en premier : motifs courants, répétitions, suites.
 */
class PasswordStrengthTest {

    @Test
    fun leMinimumEstDouzeCaracteres() {
        // Relevé de 8 à 12 le 20/09 : 8 caractères humains tombent en ~4 h hors ligne.
        assertEquals(12, PasswordStrength.MIN_LENGTH)
    }

    @Test
    fun videVautZero() {
        assertEquals(0, PasswordStrength.estimateBits(""))
        assertEquals(Level.WEAK, PasswordStrength.level(""))
        assertEquals(0f, PasswordStrength.fraction(""), 0f)
    }

    @Test
    fun unMotifCourantPlafonneTout() {
        // Peu importe ce qu'on ajoute autour de « password » : un dictionnaire l'enchaîne d'office.
        assertTrue(PasswordStrength.estimateBits("password1234") <= 20)
        assertEquals(Level.WEAK, PasswordStrength.level("password1234"))
        assertEquals(Level.WEAK, PasswordStrength.level("Shieldkey2026!"))
        assertEquals(Level.WEAK, PasswordStrength.level("MotDePasse!!2026"))
        assertEquals(Level.WEAK, PasswordStrength.level("azertyazerty99"))
    }

    @Test
    fun lesRepetitionsNeComptentPresquePas() {
        // 16 « a » : la longueur brute est bonne, la longueur utile ne vaut rien.
        assertEquals(Level.WEAK, PasswordStrength.level("aaaaaaaaaaaaaaaa"))
        assertTrue(PasswordStrength.estimateBits("aaaaaaaaaaaaaaaa") < 40)
    }

    @Test
    fun lesSuitesNeComptentPresquePas() {
        assertEquals(Level.WEAK, PasswordStrength.level("abcdefghijklmnop"))
        assertEquals(Level.WEAK, PasswordStrength.level("0123456789012345"))
    }

    @Test
    fun uneSeuleFamilleDeCaracteresResteMoyenne() {
        // 11 minuscules sans motif : de l'ordre de 48 bits estimés → « moyen », pas plus.
        assertEquals(Level.FAIR, PasswordStrength.level("maisonbleue"))
    }

    @Test
    fun quinzeMinusculesSansMotifSontBonnes() {
        assertEquals(Level.GOOD, PasswordStrength.level("chatperchejaune"))
    }

    @Test
    fun quatorzeCaracteresDesQuatreFamillesSontExcellents() {
        assertEquals(Level.STRONG, PasswordStrength.level("K7#mQ2!vX9@pL4"))
        assertEquals(1f, PasswordStrength.fraction("K7#mQ2!vX9@pL4"), 0f)
    }

    @Test
    fun laPhraseDePasseEstExcellente() {
        // Le conseil affiché (« quatre ou cinq mots sans rapport ») doit mener au vert.
        assertEquals(Level.STRONG, PasswordStrength.level("correct horse battery staple"))
    }

    @Test
    fun lesNiveauxSontOrdonnes() {
        val weak = PasswordStrength.level("aaaaaaaaaaaaaaaa")
        val fair = PasswordStrength.level("maisonbleue")
        val good = PasswordStrength.level("chatperchejaune")
        val strong = PasswordStrength.level("K7#mQ2!vX9@pL4")
        assertTrue(weak.ordinal < fair.ordinal)
        assertTrue(fair.ordinal < good.ordinal)
        assertTrue(good.ordinal < strong.ordinal)
    }

    @Test
    fun laJaugeResteEntreZeroEtUn() {
        listOf("", "a", "password", "maisonbleue", "chatperchejaune", "K7#mQ2!vX9@pL4",
            "correct horse battery staple correct horse battery staple").forEach {
            val f = PasswordStrength.fraction(it)
            assertTrue("fraction hors bornes pour « $it » : $f", f in 0f..1f)
        }
    }

    @Test
    fun plusLongVautPlusFortAFamilleEgale() {
        val court = PasswordStrength.estimateBits("chatperche")
        val long = PasswordStrength.estimateBits("chatperchejaune")
        assertTrue(long > court)
    }
}
