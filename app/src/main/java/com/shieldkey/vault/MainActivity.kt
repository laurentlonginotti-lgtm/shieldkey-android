package com.shieldkey.vault

import android.content.Context
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
