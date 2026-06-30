package com.shieldkey.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
}
