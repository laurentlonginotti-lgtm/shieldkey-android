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

package com.shieldkey.vault

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.shieldkey.vault.i18n.LocaleHelper
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.ui.ShieldKeyApp
import com.shieldkey.vault.ui.theme.ShieldKeyTheme
import java.io.File

// FragmentActivity (et non ComponentActivity) : requis par androidx BiometricPrompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hygiène : purge les documents déchiffrés temporairement pour une visionneuse
        // (cacheDir/open/). Ils sont normalement effacés au retour de la visionneuse, mais si
        // le système a tué ShieldKey pendant qu'elle était ouverte (mémoire basse), le clair
        // resterait jusqu'à la prochaine ouverture du coffre. Ici, on nettoie AVANT même
        // l'écran de déverrouillage — à chaque démarrage, sans condition.
        clearOpenCache()
        // Son d'ouverture (au 1er lancement de l'activité, pas lors des rotations).
        if (savedInstanceState == null) SoundFx.open()
        // Sécurité : interdit captures d'écran + masque l'aperçu dans le multitâche.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // Superpositions : tant que ShieldKey est au premier plan, aucune autre appli ne peut
        // dessiner par-dessus (faux champ « mot de passe » posé sur le vrai, bouton invisible
        // sous le doigt — la méthode favorite des chevaux de Troie bancaires). Android 12+ ;
        // en dessous, l'alternative (filterTouchesWhenObscured) bloquerait aussi les filtres
        // d'écran légitimes (lumière bleue…) en silence : on ne l'impose pas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        setContent {
            ShieldKeyTheme {
                ShieldKeyApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Son de fermeture quand on quitte réellement l'app.
        if (isFinishing) SoundFx.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dernière chance de nettoyage quand l'activité est détruite proprement.
        clearOpenCache()
    }

    private fun clearOpenCache() {
        try {
            File(cacheDir, "open").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { /* best-effort : jamais bloquant au démarrage */ }
    }

    // Applique la langue : celle choisie par l'utilisateur, ou celle du téléphone
    // (détection automatique) si le mode "Système" est actif.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
