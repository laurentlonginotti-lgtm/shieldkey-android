package com.shieldkey.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.i18n.LocaleHelper
import com.shieldkey.vault.ui.theme.SkEmerald2
import com.shieldkey.vault.ui.theme.SkMuted

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun choose(context: Context, code: String) {
    LocaleHelper.setSaved(context, code)
    context.findActivity()?.recreate()   // recharge l'UI dans la nouvelle langue
}

@Composable
fun LanguageButton() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    val label = when (LocaleHelper.getSaved(context)) {
        "fr" -> "Français"
        "en" -> "English"
        else -> stringResource(R.string.lang_system)
    }

    TextButton(onClick = { open = true }) {
        Text("🌐 $label", color = SkMuted, fontSize = 12.sp)
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.lang_title)) },
            text = {
                Column {
                    LangRow(stringResource(R.string.lang_system)) { choose(context, "") }
                    LangRow("Français") { choose(context, "fr") }
                    LangRow("English") { choose(context, "en") }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_cancel), color = SkEmerald2)
                }
            }
        )
    }
}

@Composable
private fun LangRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        fontSize = 16.sp
    )
}
