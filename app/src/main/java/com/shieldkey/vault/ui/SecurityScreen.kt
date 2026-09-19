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

package com.shieldkey.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.crypto.SkCrypto
import com.shieldkey.vault.ui.theme.SkBg
import com.shieldkey.vault.ui.theme.SkBgDeep
import com.shieldkey.vault.ui.theme.SkBgTop
import com.shieldkey.vault.ui.theme.SkEmerald2
import com.shieldkey.vault.ui.theme.SkGold
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.ui.theme.SkText

/**
 * Page « Sécurité » : explique, sans jargon, comment ShieldKey protège les données.
 * Sert à la fois de transparence (confiance) et de vitrine (réutilisable pour la fiche Play).
 * Les paramètres crypto sont lus depuis [SkCrypto] pour rester exacts.
 */
@Composable
fun SecurityScreen(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkBgTop, SkBg, SkBgDeep)))
    ) {
        CenteredColumn {
            Text("🛡️", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sec_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.sec_tagline), color = SkGold, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.sec_intro),
                color = SkMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            SecCard(stringResource(R.string.sec_offline_title), stringResource(R.string.sec_offline_body), stringResource(R.string.sec_offline_tech))
            SecCard(stringResource(R.string.sec_aes_title), stringResource(R.string.sec_aes_body), stringResource(R.string.sec_aes_tech))
            SecCard(
                stringResource(R.string.sec_argon_title),
                stringResource(R.string.sec_argon_body),
                stringResource(
                    R.string.sec_argon_tech,
                    SkCrypto.ARGON_ITERATIONS,
                    SkCrypto.ARGON_MEMORY_KIB / 1024,
                    SkCrypto.ARGON_PARALLELISM
                )
            )
            SecCard(stringResource(R.string.sec_envelope_title), stringResource(R.string.sec_envelope_body), stringResource(R.string.sec_envelope_tech))
            SecCard(stringResource(R.string.sec_recovery_title), stringResource(R.string.sec_recovery_body))
            SecCard(stringResource(R.string.sec_bio_title), stringResource(R.string.sec_bio_body), stringResource(R.string.sec_bio_tech))
            SecCard(stringResource(R.string.sec_clip_title), stringResource(R.string.sec_clip_body))
            SecCard(stringResource(R.string.sec_docs_title), stringResource(R.string.sec_docs_body))
            SecCard(stringResource(R.string.sec_phish_title), stringResource(R.string.sec_phish_body))

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sec_footer),
                color = SkEmerald2,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back), color = SkMuted) }
        }
    }
}

@Composable
private fun SecCard(title: String, body: String, tech: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .padding(16.dp)
    ) {
        Text(title, color = SkText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = SkMuted, fontSize = 13.sp)
        if (tech != null) {
            Spacer(Modifier.height(8.dp))
            Text(tech, color = SkEmerald2, fontSize = 11.sp)
        }
    }
}
