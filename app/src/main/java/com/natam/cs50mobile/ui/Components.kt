package com.natam.cs50mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Botão com texto curto na tela e uma descrição mais completa para o TalkBack. */
@Composable
internal fun ActionButton(
    @StringRes label: Int,
    @StringRes description: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val desc = stringResource(description)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = desc }
    ) {
        Text(text = stringResource(label), maxLines = 1)
    }
}

/**
 * Texto que o TalkBack fala sozinho quando muda (região ao vivo educada).
 * O caractere invisível alternado faz o TalkBack reler um texto igual ao anterior.
 */
@Composable
internal fun LiveText(announcement: Announcement, modifier: Modifier = Modifier, maxLines: Int = 8) {
    val forceChange = if (announcement.seq % 2L == 0L) "​" else ""
    Text(
        text = announcement.text + forceChange,
        maxLines = maxLines,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
internal fun AnnouncementCard(
    announcement: Announcement,
    title: String,
    emptyText: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            if (announcement.text.isEmpty()) {
                Text(text = emptyText, style = MaterialTheme.typography.bodyMedium)
            } else {
                LiveText(announcement)
            }
        }
    }
}
