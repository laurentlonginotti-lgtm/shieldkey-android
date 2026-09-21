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
import com.shieldkey.vault.data.BackupMeta.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Le rappel de sauvegarde : quand il parle, quand il se tait, et ce qui prime. */
@RunWith(AndroidJUnit4::class)
class BackupMetaTest {

    private val ctx get() = TestFiles.context
    private val day = 24L * 3600 * 1000
    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() = BackupMeta.reset(ctx)

    @Test
    fun seTaitTantQueRienNaChange() {
        assertTrue(BackupMeta.reminder(ctx) is Reminder.None)
    }

    @Test
    fun jamaisSauvegardeDesLaPremiereModification() {
        BackupMeta.noteChange(ctx)
        assertTrue(BackupMeta.reminder(ctx) is Reminder.Never)
    }

    @Test
    fun uneSauvegardeEteintLeRappel() {
        BackupMeta.noteChange(ctx)
        BackupMeta.noteBackup(ctx)
        assertTrue(BackupMeta.reminder(ctx) is Reminder.None)
        // Une modification juste après : la sauvegarde est récente, on ne harcèle pas.
        BackupMeta.noteChange(ctx)
        assertTrue(BackupMeta.reminder(ctx) is Reminder.None)
    }

    @Test
    fun perimeeApresTrenteJoursAvecDesModifications() {
        BackupMeta.noteBackup(ctx, at = t0)
        BackupMeta.noteChange(ctx)
        BackupMeta.noteChange(ctx)
        assertTrue(BackupMeta.reminder(ctx, now = t0 + 29 * day) is Reminder.None)
        val r = BackupMeta.reminder(ctx, now = t0 + 31 * day)
        assertTrue("attendu Stale, obtenu $r", r is Reminder.Stale)
        assertEquals(31, (r as Reminder.Stale).days)
        assertEquals(2, r.changes)
    }

    @Test
    fun jamaisPerimeeSansModification() {
        BackupMeta.noteBackup(ctx, at = t0)
        assertTrue(BackupMeta.reminder(ctx, now = t0 + 400 * day) is Reminder.None)
    }

    @Test
    fun leChangementDeMotDePassePrimeSurTout() {
        BackupMeta.noteBackup(ctx, at = t0)
        BackupMeta.notePasswordChanged(ctx)
        // Aucune modification, sauvegarde toute fraîche : le rappel parle quand même.
        assertTrue(BackupMeta.reminder(ctx, now = t0) is Reminder.PasswordChanged)
        // Et il prime sur la péremption.
        BackupMeta.noteChange(ctx)
        assertTrue(BackupMeta.reminder(ctx, now = t0 + 60 * day) is Reminder.PasswordChanged)
        // Seule une nouvelle sauvegarde l'éteint.
        BackupMeta.noteBackup(ctx)
        assertTrue(BackupMeta.reminder(ctx) is Reminder.None)
    }

    @Test
    fun laRestaurationVautSauvegarde() {
        BackupMeta.noteChange(ctx)
        BackupMeta.notePasswordChanged(ctx)
        BackupMeta.noteRestored(ctx)
        assertTrue(BackupMeta.reminder(ctx) is Reminder.None)
    }

    @Test
    fun leSeuilEstTrenteJours() {
        assertEquals(30, BackupMeta.STALE_AFTER_DAYS)
    }
}
