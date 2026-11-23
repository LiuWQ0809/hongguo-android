package com.hongguo.android

import com.hongguo.api.service.Client
import com.hongguo.api.util.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException

internal data class DramaPublisher(val mediaId: String, val name: String, val copyright: String)

internal object DramaPublishers {
    suspend fun find(seriesId: String): DramaPublisher? = runInterruptible(Dispatchers.IO) {
        val row = metadata(listOf(seriesId)).firstOrNull { it.field("book_id") == seriesId }
            ?: return@runInterruptible null
        val mediaId = row.field("media_id")
        val name = row.field("source").ifBlank { row.field("publisher") }
        if (mediaId.toLongOrNull()?.let { it > 0 } != true || name.isBlank()) return@runInterruptible null
        DramaPublisher(mediaId, name, row.field("copyright_info"))
    }

    suspend fun works(publisher: DramaPublisher, current: DramaCard): List<DramaCard> = runInterruptible(Dispatchers.IO) {
        val candidates = Client.searchNativeCatalog(publisher.name).mapNotNull { Json.optObj(it) }
            .filter { it.field("series_id").isNotBlank() && it.field("title").isNotBlank() }
            .distinctBy { it.field("series_id") }
        val ids = candidates.map { it.field("series_id") }.filterNot { it == current.id }
        val matched = metadata(ids).filter { it.field("media_id") == publisher.mediaId }
            .map { it.field("book_id") }.toSet()
        listOf(current) + candidates.filter { it.field("series_id") in matched }.map {
            DramaCard(it.field("series_id"), it.field("title"), it.field("cover"),
                Json.optInt(it["episode_cnt"], 0), it.field("abstract"))
        }
    }

    private fun metadata(ids: List<String>): List<Map<String, Any>> {
        if (ids.isEmpty()) return emptyList()
        val response = Client.api("GET", "/reading/bookapi/multi-detail/v1/", null,
            mapOf("book_id" to ids.joinToString(","), "book_type" to "2"), 2, true)
        if (Json.optInt(response["code"], -1) != 0) throw IOException("读取短剧发布方失败")
        return Json.optArr(response["data"]).mapNotNull { Json.optObj(it) }
    }

    private fun Map<String, Any>.field(key: String): String = Json.optStr(get(key), "").trim()
}
