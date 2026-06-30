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
