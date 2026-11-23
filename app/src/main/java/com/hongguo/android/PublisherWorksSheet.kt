package com.hongguo.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PublisherWorksSheet(publisher: DramaPublisher, current: DramaCard, onDismiss: () -> Unit,
                                 onOpen: (DramaCard) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var works by remember(publisher.mediaId, current.id) { mutableStateOf<List<DramaCard>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(publisher.mediaId, current.id, retry) {
        failed = false
        try { works = DramaPublishers.works(publisher, current) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().testTag("publisher_works"), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(publisher.name, style = MaterialTheme.typography.titleLarge)
                    if (publisher.copyright.isNotBlank())
                        Text(publisher.copyright, style = MaterialTheme.typography.bodySmall)
                    Text("已核验的同发布方作品", style = MaterialTheme.typography.titleSmall)
                    when {
                        failed -> TextButton(onClick = { retry++ }) { Text("作品加载失败，点击重试") }
                        works == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        works?.size == 1 -> Text("暂未找到更多作品", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(works ?: listOf(current), key = { it.id }) { card ->
                ListItem(headlineContent = { Text(card.title) },
                    supportingContent = if (card.id == current.id) ({ Text("当前作品") }) else null,
                    modifier = if (card.id == current.id) Modifier else Modifier.clickable { onDismiss(); onOpen(card) })
            }
        }
    }
}
