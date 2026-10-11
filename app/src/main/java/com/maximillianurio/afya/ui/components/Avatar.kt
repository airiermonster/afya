package com.maximillianurio.afya.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.afya
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The person as they chose to appear: their photo if they picked one, their initials if they only
 * wrote a name, and the plain glyph if they wrote nothing. The photo is a JPEG the app shrunk into
 * its own private directory at pick time, decoded off the main thread.
 */
@Composable
fun AfyaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    size: Dp = 68.dp,
    onClick: (() -> Unit)? = null,
) {
    val afya = MaterialTheme.afya
    val context = LocalContext.current
    val photo by rememberAvatarBitmap(File(context.filesDir, fileName.orEmpty()))
    val label = name.trim()

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(
                    onClickLabel = stringResource(
                        if (fileName == null) R.string.avatar_add else R.string.avatar_change
                    ),
                    onClick = onClick,
                )
            )
            .clip(CircleShape)
            .background(if (photo == null) afya.brandWash else afya.cardTint),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = label.ifBlank { stringResource(R.string.avatar_photo) },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            label.isNotBlank() -> Text(
                text = initialsOf(label),
                style = MaterialTheme.typography.titleLarge,
                color = afya.brandDeep,
            )

            else -> Icon(
                imageVector = AfyaIcons.You,
                contentDescription = stringResource(R.string.tab_you),
                tint = afya.brandDeep,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
private fun rememberAvatarBitmap(file: File) =
    produceState<ImageBitmap?>(initialValue = null, key1 = file) {
        value = if (file.isFile) withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

private fun initialsOf(name: String): String =
    name.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase().toString() }
