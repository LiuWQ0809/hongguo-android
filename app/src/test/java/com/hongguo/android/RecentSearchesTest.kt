package com.hongguo.android

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test fun newestFirstDeduplicatedAndBoundedAcrossReload() {
        val history = (1..10).fold(emptyList<String>()) { list, n -> RecentSearches.add(list, " 剧$n ") }
        val updated = RecentSearches.add(history, "剧5")
        assertEquals(listOf("剧5", "剧10", "剧9", "剧8", "剧7", "剧6", "剧4", "剧3"), updated)
        assertEquals(updated, RecentSearches.decode(RecentSearches.encode(updated)))
    }

    @Test fun invalidDataAndOversizedQueriesAreIgnored() {
        assertEquals(emptyList<String>(), RecentSearches.decode("not json"))
        assertEquals(emptyList<String>(), RecentSearches.decode("x".repeat(4_097)))
        assertEquals(listOf("已有"), RecentSearches.add(listOf("已有"), "x".repeat(129)))
        assertEquals(listOf("已有"), RecentSearches.add(listOf("已有"), "  "))
    }
}
