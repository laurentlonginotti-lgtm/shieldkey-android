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

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Presse-papier durci pour les champs sensibles (mot de passe, CVV, IBAN, seed, clé privée) :
 *  - marque le contenu « sensible » sur Android 13+ (masqué de l'aperçu, de l'historique
 *    du presse-papier et des suggestions clavier) ;
 *  - efface automatiquement le presse-papier après [CLEAR_DELAY_MS] ;
 *  - efface aussi à la demande (au verrouillage du coffre — moment le plus fiable, app au
 *    premier plan).
 *
 * On étiquette nos copies avec [LABEL] et on ne nettoie que si le presse-papier porte encore
 * cette étiquette : lecture des seules MÉTADONNÉES (pas du contenu) → n'écrase pas une copie
 * que l'utilisateur aurait faite ailleurs et ne déclenche pas le bandeau « accès au presse-papier ».
 */
object SecureClipboard {

    private const val LABEL = "ShieldKey"
    private const val CLEAR_DELAY_MS = 45_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Copie une valeur sensible et programme son effacement automatique. */
    fun copySensitive(context: Context, value: String) {
        val cm = context.clipboard() ?: return
        val clip = ClipData.newPlainText(LABEL, value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { cm.setPrimaryClip(clip) }.onFailure { return }

        val appCtx = context.applicationContext
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { clearIfOurs(appCtx) }
        pending = r
        handler.postDelayed(r, CLEAR_DELAY_MS)
    }

    /**
     * Efface le presse-papier s'il contient (encore) une valeur posée par ShieldKey.
     * `label == null` = illisible (arrière-plan) ou vide → on efface par sécurité.
     * Un autre label = l'utilisateur a copié autre chose → on n'y touche pas.
     */
    fun clearIfOurs(context: Context) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        val cm = context.clipboard() ?: return
        val label = runCatching { cm.primaryClipDescription?.label?.toString() }.getOrNull()
        if (label == null || label == LABEL) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    private fun Context.clipboard(): ClipboardManager? =
        getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
}
