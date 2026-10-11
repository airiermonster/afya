package com.maximillianurio.afya.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.ui.theme.afya

/**
 * Asks for the backup passphrase. When [optional], an empty field is a real answer — the backup is
 * written as plain JSON, as it always was — and the confirm label says which of the two will happen.
 */
@Composable
fun PassphraseDialog(
    title: String,
    message: String,
    fieldLabel: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    optional: Boolean = false,
    plainLabel: String = confirmLabel,
    error: String? = null,
) {
    val afya = MaterialTheme.afya
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = afya.ink) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = afya.inkMuted)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(128) },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = optional || text.isNotEmpty(),
            ) {
                Text(
                    text = if (optional && text.isEmpty()) plainLabel else confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = afya.brand,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = MaterialTheme.typography.labelLarge, color = afya.inkMuted)
            }
        },
    )
}
