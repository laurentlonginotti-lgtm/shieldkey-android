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

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Les tests instrumentés partagent le vrai `filesDir` de l'appli sur l'émulateur : chaque test
 * repart d'un téléphone vierge, et le laisse vierge.
 */
object TestFiles {

    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Mot de passe maître de référence (≥ 12 caractères, comme l'appli l'exige). */
    const val PWD = "quatre-mots-au-hasard"
    const val WRONG = "pas-le-bon-mot-de-passe"
    const val NEW_PWD = "encore-un-autre-mot-de-passe"

    fun vaultFile(): File = File(context.filesDir, "vault.skv")

    /** Aucun coffre, aucun document, aucun déverrouillage rapide, aucun compteur. */
    fun wipe() {
        val ctx = context
        File(ctx.filesDir, "vault.skv").delete()
        File(ctx.filesDir, "vault.skv.tmp").delete()
        File(ctx.filesDir, "bio.skv").delete()
        File(ctx.filesDir, "docs").deleteRecursively()
        BackupMeta.reset(ctx)
    }
}
