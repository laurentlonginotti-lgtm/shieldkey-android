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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldkey.vault.data.TestFiles.NEW_PWD
import com.shieldkey.vault.data.TestFiles.PWD
import com.shieldkey.vault.data.TestFiles.WRONG
import com.shieldkey.vault.data.VaultStore.UnlockResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Le fichier `.skb` : le seul objet de ShieldKey qui sort du téléphone, donc le plus exposé. */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private val ctx get() = TestFiles.context

    @Before
    fun setUp() = TestFiles.wipe()

    @After
    fun tearDown() = TestFiles.wipe()

    /** Un coffre avec une entrée et un document, comme un vrai. Renvoie la DEK. */
    private fun fillVault(): ByteArray {
        val store = VaultStore(ctx)
        val dek = store.create(PWD).dek
        VaultRepository.upsertEntry(
            store, dek,
            VaultEntry("e1", EntryType.LOGIN, "Banque", mapOf("username" to "laurent", "password" to "s3cret"), 1L)
        )
        val id = DocumentStore(ctx).save(dek, "bonjour".toByteArray())
        VaultRepository.addDocument(store, dek, DocumentMeta(id, "note.txt", "text/plain", 7L, 2L))
        return dek
    }

    private fun unlock(pwd: String): ByteArray =
        (VaultStore(ctx).unlock(pwd) as? UnlockResult.Success)?.dek
            ?: throw AssertionError("le coffre restauré ne s'ouvre pas avec « $pwd »")

    @Test
    fun allerRetourCompletEntreesEtDocuments() {
        fillVault()
        val skb = BackupManager.export(ctx, PWD)
        assertTrue(String(skb, Charsets.UTF_8).startsWith("SHIELDKEY-BACKUP-1\n"))

        TestFiles.wipe()   // « nouveau téléphone »
        assertFalse(VaultStore(ctx).isInitialized())

        assertTrue(BackupManager.import(ctx, skb, PWD))
        val store = VaultStore(ctx)
        val dek = unlock(PWD)

        val entries = VaultRepository.listEntries(store, dek)
        assertEquals(1, entries.size)
        assertEquals("Banque", entries[0].title)
        assertEquals(EntryType.LOGIN, entries[0].type)
        assertEquals("s3cret", entries[0].fields["password"])

        val docs = VaultRepository.listDocuments(store, dek)
        assertEquals(1, docs.size)
        assertEquals("note.txt", docs[0].name)
        assertEquals("bonjour", String(DocumentStore(ctx).read(dek, docs[0].id), Charsets.UTF_8))
    }

    @Test
    fun laSauvegardeNeRevelePasSaStructure() {
        // Rien de lisible après l'en-tête : ni « vault », ni « docs », ni un nom de fichier.
        fillVault()
        val text = String(BackupManager.export(ctx, PWD), Charsets.UTF_8)
        val body = text.substringAfter('\n')
        assertFalse(body.contains("vault"))
        assertFalse(body.contains("masterWrap"))
        assertFalse(body.contains("note.txt"))
    }

    @Test
    fun deuxExportsDuMemeCoffreDifferent() {
        // Sel et IV neufs à chaque export : deux fichiers identiques trahiraient une clé réutilisée.
        fillVault()
        val a = BackupManager.export(ctx, PWD)
        val b = BackupManager.export(ctx, PWD)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun unMauvaisMotDePasseEstRefuseEtNecritRien() {
        fillVault()
        val skb = BackupManager.export(ctx, PWD)
        TestFiles.wipe()
        assertFalse(BackupManager.import(ctx, skb, WRONG))
        assertFalse(VaultStore(ctx).isInitialized())
        assertFalse(File(ctx.filesDir, "docs").listFiles()?.isNotEmpty() ?: false)
    }

    @Test
    fun unFichierAltereEstRefuse() {
        fillVault()
        val text = String(BackupManager.export(ctx, PWD), Charsets.UTF_8)
        val tampered = (text.dropLast(8) + "AAAAAAAA").toByteArray(Charsets.UTF_8)
        TestFiles.wipe()
        assertFalse(BackupManager.import(ctx, tampered, PWD))
        assertFalse(VaultStore(ctx).isInitialized())
    }

    @Test
    fun unFichierQuiNestPasUneSauvegardeEstRefuse() {
        assertFalse(BackupManager.import(ctx, "bonjour".toByteArray(), PWD))
        assertFalse(BackupManager.import(ctx, ByteArray(0), PWD))
        assertFalse(BackupManager.import(ctx, "SHIELDKEY-BACKUP-1\nAAAA".toByteArray(), PWD))
    }

    @Test
    fun unEchecNeTouchePasAuCoffreExistant() {
        fillVault()
        val before = TestFiles.vaultFile().readBytes()
        val docsBefore = File(ctx.filesDir, "docs").list()!!.sorted()
        val skb = BackupManager.export(ctx, PWD)

        assertFalse(BackupManager.import(ctx, skb, WRONG))

        assertArrayEquals(before, TestFiles.vaultFile().readBytes())
        assertEquals(docsBefore, File(ctx.filesDir, "docs").list()!!.sorted())
    }

    @Test
    fun laRestaurationRemplaceLeCoffreEtSesDocuments() {
        // Coffre A sauvegardé ; coffre B créé par-dessus ; restaurer A doit effacer B — y compris
        // ses documents, qui ne doivent pas rester en orphelins sur le disque.
        fillVault()
        val skbA = BackupManager.export(ctx, PWD)

        TestFiles.wipe()
        val storeB = VaultStore(ctx)
        val dekB = storeB.create(NEW_PWD).dek
        val orphan = DocumentStore(ctx).save(dekB, "B".toByteArray())

        assertTrue(BackupManager.import(ctx, skbA, PWD))
        val dek = unlock(PWD)
        assertEquals("Banque", VaultRepository.listEntries(VaultStore(ctx), dek)[0].title)
        assertFalse(File(File(ctx.filesDir, "docs"), "$orphan.blob").exists())
        assertTrue(VaultStore(ctx).unlock(NEW_PWD) is UnlockResult.WrongPassword)
    }

    @Test
    fun uneSauvegardeGardeLeMotDePasseDuMomentDeLExport() {
        // C'est le point que l'écran de changement de mot de passe annonce : un .skb exporté
        // AVANT le changement ne connaît que l'ANCIEN mot de passe.
        val dek = fillVault()
        val skbOld = BackupManager.export(ctx, PWD)
        VaultStore(ctx).changeMasterPassword(dek, NEW_PWD, renewRecovery = false)
        val skbNew = BackupManager.export(ctx, NEW_PWD)

        TestFiles.wipe()
        assertFalse(BackupManager.import(ctx, skbOld, NEW_PWD))
        assertTrue(BackupManager.import(ctx, skbOld, PWD))
        unlock(PWD)

        TestFiles.wipe()
        assertTrue(BackupManager.import(ctx, skbNew, NEW_PWD))
        unlock(NEW_PWD)
    }

    @Test
    fun uneRestaurationVautSauvegardeAJour() {
        fillVault()
        val skb = BackupManager.export(ctx, PWD)
        TestFiles.wipe()
        BackupMeta.noteChange(ctx)   // un rappel en attente…
        assertTrue(BackupManager.import(ctx, skb, PWD))
        assertTrue(BackupMeta.reminder(ctx) is BackupMeta.Reminder.None)   // …éteint par la restauration
    }
}
