package com.redwolf.axix.network


import kotlinx.coroutines.delay

data class FeedItem(val id: String, val title: String)

interface FeedApi {
    suspend fun getFeed(page: Int, size: Int): List<FeedItem>
}

class MockFeedApi : FeedApi {
    private val total = 87
    override suspend fun getFeed(page: Int, size: Int): List<FeedItem> {
        delay(250)
        val start = (page - 1) * size
        if (start >= total) return emptyList()
        val endExclusive = kotlin.math.min(total, start + size)
        return (start until endExclusive).map { i -> FeedItem("id_$i", "标题 #$i（第 $page 页）") }
    }
}
