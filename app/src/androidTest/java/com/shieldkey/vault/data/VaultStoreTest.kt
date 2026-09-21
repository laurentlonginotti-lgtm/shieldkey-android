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

package com.shieldkey.vault.data

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldkey.vault.crypto.SkCrypto
import com.shieldkey.vault.data.TestFiles.NEW_PWD
import com.shieldkey.vault.data.TestFiles.PWD
import com.shieldkey.vault.data.TestFiles.WRONG
import com.shieldkey.vault.data.VaultStore.UnlockResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le fichier du coffre, de bout en bout, avec le vrai Argon2id : création, déverrouillage,
 * freinage, récupération, changement de mot de passe, et les trois diagnostics de corruption.
 * Chaque dérivation coûte ~1 s sur l'émulateur ; les tests en font le moins possible.
 */
@RunWith(AndroidJUnit4::class)
class VaultStoreTest {

    private lateinit var store: VaultStore

    @Before
    fun setUp() {
        TestFiles.wipe()
        store = VaultStore(TestFiles.context)
    }

    @After
    fun tearDown() = TestFiles.wipe()

    private fun dekOf(res: UnlockResult): ByteArray =
        (res as? UnlockResult.Success)?.dek ?: throw AssertionError("attendu Success, obtenu $res")

    // --- création / déverrouillage --------------------------------------------------------

    @Test
    fun creeUnCoffreVideEtLeRouvre() {
        assertFalse(store.isInitialized())
        val created = store.create(PWD)
        assertTrue(store.isInitialized())
        assertEquals(SkCrypto.KEY_LEN, created.dek.size)

        val dek = dekOf(store.unlock(PWD))
        assertArrayEquals(created.dek, dek)

        val root = JSONObject(store.readVault(dek))
        assertEquals(0, root.getJSONArray("items").length())
    }

    @Test
    fun refuseUnMauvaisMotDePasse() {
        store.create(PWD)
        assertTrue(store.unlock(WRONG) is UnlockResult.WrongPassword)
    }

    @Test
    fun deuxCoffresNontPasLeMemeSel() {
        store.create(PWD)
        val a = JSONObject(TestFiles.vaultFile().readText())
        TestFiles.wipe()
        store.create(PWD)
        val b = JSONObject(TestFiles.vaultFile().readText())
        assertNotEquals(a.getString("masterSalt"), b.getString("masterSalt"))
        assertNotEquals(a.getString("masterWrap"), b.getString("masterWrap"))
    }

    // --- freinage ---------------------------------------------------------------------------

    @Test
    fun freineAuCinquiemeEchec() {
        store.create(PWD)
        repeat(4) { assertTrue(store.unlock(WRONG) is UnlockResult.WrongPassword) }
        val fifth = store.unlock(WRONG)
        assertTrue("attendu Throttled, obtenu $fifth", fifth is UnlockResult.Throttled)
        assertEquals(15L, (fifth as UnlockResult.Throttled).secondsLeft)
        // Pendant l'attente, même le BON mot de passe est refusé : pas d'oracle.
        assertTrue(store.unlock(PWD) is UnlockResult.Throttled)
    }

    @Test
    fun leBonMotDePasseRemetLeCompteurAZero() {
        store.create(PWD)
        repeat(3) { store.unlock(WRONG) }
        dekOf(store.unlock(PWD))
        // Compteur reparti de zéro : 4 nouveaux échecs ne freinent pas encore.
        repeat(4) { assertTrue(store.unlock(WRONG) is UnlockResult.WrongPassword) }
    }

    // --- récupération -----------------------------------------------------------------------

    @Test
    fun leCodeDeSecoursOuvreLaMemeDek() {
        val created = store.create(PWD)
        assertArrayEquals(created.dek, dekOf(store.unlockWithRecovery(created.recoveryCode)))
    }

    @Test
    fun unMauvaisCodeDeSecoursEstRefuse() {
        store.create(PWD)
        assertTrue(store.unlockWithRecovery("AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA") is UnlockResult.WrongPassword)
    }

    @Test
    fun leCodeDeSecoursTolereLesConfusionsVisuelles() {
        // Minuscules, espaces au lieu de tirets, l pour 1, o pour 0 : tout doit passer.
        val created = store.create(PWD)
        val typed = created.recoveryCode.lowercase().replace('-', ' ').replace('1', 'l').replace('0', 'o')
        dekOf(store.unlockWithRecovery(typed))
    }

    @Test
    fun laRecuperationRenouvelleMotDePasseEtCode() {
        val created = store.create(PWD)
        val dek = dekOf(store.unlockWithRecovery(created.recoveryCode))

        val newCode = store.resetMasterPassword(dek, NEW_PWD)
        assertNotEquals(created.recoveryCode, newCode)

        dekOf(store.unlock(NEW_PWD))
        assertTrue(store.unlock(PWD) is UnlockResult.WrongPassword)
        // L'ancien code est révoqué (sinon une feuille photographiée resterait une clé à vie).
        assertTrue(store.unlockWithRecovery(created.recoveryCode) is UnlockResult.WrongPassword)
        assertArrayEquals(created.dek, dekOf(store.unlockWithRecovery(newCode)))
    }

    // --- changement de mot de passe ---------------------------------------------------------

    @Test
    fun leChangementConserveLeCodeDeSecoursParDefaut() {
        val created = store.create(PWD)
        assertNull(store.changeMasterPassword(created.dek, NEW_PWD, renewRecovery = false))
        dekOf(store.unlock(NEW_PWD))
        assertTrue(store.unlock(PWD) is UnlockResult.WrongPassword)
        assertArrayEquals(created.dek, dekOf(store.unlockWithRecovery(created.recoveryCode)))
    }

    @Test
    fun leChangementPeutRenouvelerLeCodeDeSecours() {
        val created = store.create(PWD)
        val code = store.changeMasterPassword(created.dek, NEW_PWD, renewRecovery = true)
        assertNotNull(code)
        assertTrue(store.unlockWithRecovery(created.recoveryCode) is UnlockResult.WrongPassword)
        assertArrayEquals(created.dek, dekOf(store.unlockWithRecovery(code!!)))
    }

    @Test
    fun leChangementRemetLeFreinageAZero() {
        val created = store.create(PWD)
        repeat(5) { store.unlock(WRONG) }
        assertTrue(store.unlock(PWD) is UnlockResult.Throttled)
        store.changeMasterPassword(created.dek, NEW_PWD, renewRecovery = false)
        dekOf(store.unlock(NEW_PWD))
    }

    @Test
    fun leContenuSurvitAuChangementDeMotDePasse() {
        // Seul l'emballage de la DEK change : le contenu, chiffré par la DEK, reste tel quel.
        val created = store.create(PWD)
        val json = JSONObject().put("version", 1).put("items", JSONArray().put("secret")).toString()
        store.writeVault(created.dek, json)
        val vaultBefore = JSONObject(TestFiles.vaultFile().readText()).getString("vault")

        store.changeMasterPassword(created.dek, NEW_PWD, renewRecovery = false)

        val vaultAfter = JSONObject(TestFiles.vaultFile().readText()).getString("vault")
        assertEquals(vaultBefore, vaultAfter)
        assertEquals(json, store.readVault(dekOf(store.unlock(NEW_PWD))))
    }

    // --- contenu ----------------------------------------------------------------------------

    @Test
    fun ecritEtRelitLeContenu() {
        val created = store.create(PWD)
        val json = JSONObject().put("version", 1).put("items", JSONArray().put("x")).toString()
        store.writeVault(created.dek, json)
        assertEquals(json, store.readVault(created.dek))
        assertTrue(store.vaultDecrypts(created.dek))
        assertFalse(store.vaultDecrypts(SkCrypto.randomBytes(SkCrypto.KEY_LEN)))
    }

    // --- corruption -------------------------------------------------------------------------

    @Test
    fun unCoffreSainNestJamaisDiagnostiqueCorrompu() {
        store.create(PWD)
        assertFalse(store.isCorrupted())
    }

    @Test
    fun unFichierIllisibleEstDiagnostiqueSansMotDePasse() {
        store.create(PWD)
        TestFiles.vaultFile().writeText("ceci n'est pas du JSON")
        assertTrue(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
        assertTrue(store.unlockWithRecovery("AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA") is UnlockResult.Corrupted)
    }

    @Test
    fun unChampManquantEstUneCorruption() {
        store.create(PWD)
        val root = JSONObject(TestFiles.vaultFile().readText())
        root.remove("recoveryWrap")
        TestFiles.vaultFile().writeText(root.toString())
        assertTrue(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
    }

    @Test
    fun unBase64AltereEstUneCorruption() {
        store.create(PWD)
        val root = JSONObject(TestFiles.vaultFile().readText())
        root.put("masterSalt", "%%pas du base64%%")
        TestFiles.vaultFile().writeText(root.toString())
        assertTrue(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
    }

    /** Altère un octet au milieu du champ « vault » (base64 valide, contenu faux). */
    private fun tamperVault(root: JSONObject) {
        val bytes = Base64.decode(root.getString("vault"), Base64.NO_WRAP)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        root.put("vault", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    @Test
    fun unContenuAltereEstDetecteParLEmpreinteSansMotDePasse() {
        store.create(PWD)
        val f = TestFiles.vaultFile()
        val root = JSONObject(f.readText())
        tamperVault(root)
        f.writeText(root.toString())

        // L'empreinte parle avant toute dérivation : bon ou mauvais mot de passe, c'est le fichier.
        assertTrue(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
        assertTrue(store.unlock(WRONG) is UnlockResult.Corrupted)
    }

    @Test
    fun sansEmpreinteLeBonMotDePasseReveleLaCorruptionEtLeMauvaisResteMauvais() {
        // Fichier d'une version antérieure (pas d'empreinte) dont le contenu est abîmé : GCM sous
        // la DEK est alors le seul juge. Le mauvais mot de passe, lui, reste « mauvais ».
        store.create(PWD)
        val f = TestFiles.vaultFile()
        val root = JSONObject(f.readText())
        tamperVault(root)
        root.remove("checksum")
        f.writeText(root.toString())

        assertFalse(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
        assertTrue(store.unlock(WRONG) is UnlockResult.WrongPassword)
    }

    @Test
    fun unFichierSansEmpreinteResteLisibleEtEnRecoitUne() {
        // Migration silencieuse : un coffre d'avant s'ouvre, et sa prochaine écriture le dote
        // de l'empreinte — sans rien demander à l'utilisateur.
        store.create(PWD)
        val f = TestFiles.vaultFile()
        val root = JSONObject(f.readText())
        root.remove("checksum")
        f.writeText(root.toString())

        assertFalse(store.isCorrupted())
        val dek = dekOf(store.unlock(PWD))
        store.writeVault(dek, store.readVault(dek))
        assertTrue(JSONObject(f.readText()).has("checksum"))
        assertFalse(store.isCorrupted())
    }

    @Test
    fun unSelDeMauvaiseTailleEstUneCorruptionMemeSansEmpreinte() {
        store.create(PWD)
        val f = TestFiles.vaultFile()
        val root = JSONObject(f.readText())
        root.put("masterSalt", Base64.encodeToString(ByteArray(8), Base64.NO_WRAP))
        root.remove("checksum")
        f.writeText(root.toString())
        assertTrue(store.isCorrupted())
        assertTrue(store.unlock(PWD) is UnlockResult.Corrupted)
    }

    @Test
    fun lEmpreinteSuitChaqueEcriture() {
        val created = store.create(PWD)
        val before = JSONObject(TestFiles.vaultFile().readText()).getString("checksum")
        store.writeVault(created.dek, JSONObject().put("version", 1).put("items", JSONArray()).toString())
        val after = JSONObject(TestFiles.vaultFile().readText()).getString("checksum")
        assertNotEquals(before, after)
        assertFalse(store.isCorrupted())
    }
}
