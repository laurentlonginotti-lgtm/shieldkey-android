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

package com.shieldkey.vault.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Gestion de la langue de l'app.
 *  - "" (vide)  = suit la langue du téléphone (par défaut)
 *  - "fr" / "en" = force cette langue
 *
 * La préférence est appliquée dans Activity.attachBaseContext (voir MainActivity)
 * en enveloppant le contexte avec la locale choisie. Hors-ligne, aucune permission.
 */
object LocaleHelper {

    private const val PREF = "sk_prefs"
    private const val KEY = "lang"

    fun getSaved(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun setSaved(context: Context, lang: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, lang).apply()
    }

    fun wrap(context: Context): Context {
        val lang = getSaved(context)
        if (lang.isEmpty()) return context
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
