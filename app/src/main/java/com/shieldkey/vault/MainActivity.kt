package com.shieldkey.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.ui.theme.SkBg
import com.shieldkey.vault.ui.theme.SkBgDeep
import com.shieldkey.vault.ui.theme.SkBgTop
import com.shieldkey.vault.ui.theme.SkEmeraldGlow
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.sound.SoundFx
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
                LockScreen()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Son de fermeture quand on quitte réellement l'app.
        if (isFinishing) SoundFx.close()
    }
}

@Composable
fun LockScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkBgTop, SkBg, SkBgDeep))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = "ShieldKey",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "ShieldKey",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Votre coffre est verrouillé",
                color = SkMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(SkEmeraldGlow),
                contentAlignment = Alignment.Center
            ) {
                Text("👆", fontSize = 32.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "🔒  100 % hors-ligne · aucune donnée ne quitte ce téléphone",
                color = SkMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
        }
    }
}
