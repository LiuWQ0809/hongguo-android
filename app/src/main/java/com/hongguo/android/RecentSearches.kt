package com.hongguo.android

import com.hongguo.api.util.Json

/** Ordered, bounded local search history; malformed stored data is ignored. */
internal object RecentSearches {
    const val KEY = "recent_searches"
    private const val LIMIT = 8

    fun decode(raw: String?): List<String> = try {
        if (raw == null || raw.length > 4_096) emptyList()
        else Json.optArr(Json.parse(raw)).mapNotNull { it as? String }
            .map(String::trim).filter { it.isNotEmpty() && it.length <= 128 }.distinct().take(LIMIT)
    } catch (_: Exception) { emptyList() }

    fun add(previous: List<String>, query: String): List<String> {
        val clean = query.trim()
        if (clean.isEmpty() || clean.length > 128) return previous
        return (listOf(clean) + previous).distinct().take(LIMIT)
    }

    fun encode(values: List<String>): String = Json.stringify(values)
}
