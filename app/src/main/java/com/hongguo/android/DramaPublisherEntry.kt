package com.hongguo.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberDramaPublisher(seriesId: String): DramaPublisher? {
    var publisher by remember(seriesId) { mutableStateOf<DramaPublisher?>(null) }
    LaunchedEffect(seriesId) {
        publisher = try { DramaPublishers.find(seriesId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    return publisher
}

@Composable
internal fun PublisherButton(publisher: DramaPublisher, color: Color = MaterialTheme.colorScheme.primary,
                             onClick: () -> Unit) {
    Text("发布方 · ${publisher.name} ›",
        Modifier.testTag("drama_publisher").clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun DramaPublisherEntry(card: DramaCard, model: HongguoViewModel) {
    val publisher = rememberDramaPublisher(card.id) ?: return
    var opened by remember(card.id) { mutableStateOf(false) }
    PublisherButton(publisher) { opened = true }
    if (opened) PublisherWorksSheet(publisher, card, { opened = false }, model::openDrama)
}
