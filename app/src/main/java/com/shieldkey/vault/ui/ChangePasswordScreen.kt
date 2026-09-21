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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.ui.theme.SkEmerald
import com.shieldkey.vault.ui.theme.SkGold
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.ui.theme.SkOnPrimary
import com.shieldkey.vault.ui.theme.SkText
import com.shieldkey.vault.util.PasswordStrength
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
//  Changement du mot de passe maître (coffre ouvert)
// ---------------------------------------------------------------------------

/**
 * Trois garde-fous, dans l'ordre :
 *  - le mot de passe ACTUEL est exigé — un téléphone laissé déverrouillé ne doit pas permettre
 *    de s'approprier le coffre en remplaçant sa clé ; il est vérifié par [VaultStore.unlock]
 *    et traverse donc le freinage des essais comme à l'écran de déverrouillage ;
 *  - le nouveau doit atteindre la longueur minimale, relevée à 12 après coup : cet écran est
 *    précisément ce qui permet à un coffre créé avec 8 caractères de se mettre à niveau ;
 *  - le nouveau doit différer de l'actuel, sinon l'opération ne protège de rien.
 *
 * Le code de secours n'est renouvelé que sur demande (voir [VaultStore.changeMasterPassword]) ;
 * dans ce cas l'hôte prend la main pour afficher le nouveau kit, et cet écran disparaît.
 */
@Composable
fun ChangePasswordScreen(
    /** Renvoie null si le changement a réussi, sinon le message d'erreur à afficher. */
    onSubmit: suspend (current: String, newPassword: String, renewRecovery: Boolean) -> String?,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var renew by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val tooShort = pwd.isNotEmpty() && pwd.length < PasswordStrength.MIN_LENGTH
    val same = pwd.isNotEmpty() && pwd == current
    val mismatch = confirm.isNotEmpty() && confirm != pwd
    val canSubmit = current.isNotEmpty() && pwd.length >= PasswordStrength.MIN_LENGTH &&
        !same && pwd == confirm && !loading

    if (done) {
        CenteredColumn {
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.chpwd_done_title), color = SkEmerald, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.chpwd_done_body), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            SkPrimaryButton(stringResource(R.string.chpwd_done_back)) { onDone() }
        }
        return
    }

    CenteredColumn {
        Text(stringResource(R.string.chpwd_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.chpwd_subtitle), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        SkPasswordField(current, { current = it; errMsg = null }, stringResource(R.string.field_current_password))
        Spacer(Modifier.height(14.dp))
        SkPasswordField(pwd, { pwd = it; errMsg = null }, stringResource(R.string.field_new_master))
        PasswordStrengthMeter(pwd)
        if (tooShort) HintText(stringResource(R.string.err_min_len, PasswordStrength.MIN_LENGTH), warn = true)
        else if (same) HintText(stringResource(R.string.chpwd_same), warn = true)
        Spacer(Modifier.height(10.dp))
        SkPasswordField(confirm, { confirm = it; errMsg = null }, stringResource(R.string.field_confirm))
        if (mismatch) HintText(stringResource(R.string.err_mismatch), warn = true)
        errMsg?.let { HintText(it, warn = true) }
        Spacer(Modifier.height(16.dp))
        // Renouvellement du code de secours : facultatif, éteint par défaut (voir VaultStore).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .clickable { renew = !renew }
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chpwd_renew),
                    color = SkText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = renew,
                    onCheckedChange = { renew = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SkOnPrimary,
                        checkedTrackColor = SkEmerald
                    )
                )
            }
            Text(
                stringResource(R.string.chpwd_renew_hint),
                color = SkMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // Les .skb déjà exportés gardent l'ancien mot de passe : c'est le point que l'utilisateur
        // ne peut pas deviner, et celui qui annule le bénéfice du changement s'il l'ignore.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1AFFC857))
                .padding(12.dp)
        ) {
            Text(stringResource(R.string.chpwd_backup_note), color = SkGold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        SkPrimaryButton(stringResource(R.string.chpwd_action), enabled = canSubmit, loading = loading) {
            scope.launch {
                loading = true
                val msg = onSubmit(current, pwd, renew)
                if (msg == null) {
                    done = true
                } else {
                    errMsg = msg
                    current = ""
                    SoundFx.error()
                }
                loading = false
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
    }
}
