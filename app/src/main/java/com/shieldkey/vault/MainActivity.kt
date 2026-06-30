package com.shieldkey.vault

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shieldkey.vault.i18n.LocaleHelper
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.ui.ShieldKeyApp
import com.shieldkey.vault.ui.theme.ShieldKeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // Applique la langue : celle choisie par l'utilisateur, ou celle du téléphone
    // (détection automatique) si le mode "Système" est actif.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
