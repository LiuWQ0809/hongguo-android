package com.hongguo.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 按需读取同系列和同 IP 内容，不延迟剧集详情或播放。 */
@Composable
internal fun RelatedWorks(card: DramaCard, model: HongguoViewModel) {
    val scope = rememberCoroutineScope()
    var works by remember(card.id) { mutableStateOf<List<DramaCard>?>(null) }
    var expanded by remember(card.id) { mutableStateOf(false) }
    var loading by remember(card.id) { mutableStateOf(false) }
    var failed by remember(card.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TextButton(onClick = {
            if (works != null) expanded = !expanded
            else if (!loading) {
                loading = true
                failed = false
                scope.launch {
                    try { works = DramaRepository().related(card.id); expanded = true }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                    finally { loading = false }
                }
            }
        }, modifier = Modifier.testTag("related_load"), enabled = !loading) {
            Text(when { loading -> "正在获取相关作品…"; failed -> "相关作品获取失败，点击重试"
                expanded -> "收起相关作品（${works!!.size}）"; works != null -> "相关作品（${works!!.size}）"; else -> "查看相关作品" })
        }
        if (expanded) {
            if (works.isNullOrEmpty()) Text("暂无相关作品")
            works.orEmpty().forEach { related ->
                TextButton(onClick = { model.openDrama(related) }, enabled = !related.upcoming,
                    modifier = Modifier.fillMaxWidth().testTag("related_${related.id}")) {
                    Text(related.title + when { related.upcoming -> " · 即将上线"; related.episodeCount > 0 -> " · ${related.episodeCount}集"
                        else -> " · 暂无集数信息" })
                }
            }
        }
    }
}
