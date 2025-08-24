package com.redwolf.axix.home

import com.redwolf.mvi_dynamic_plugin.AxMviAction
import com.redwolf.mvi_dynamic_plugin.Reducer
import com.redwolf.mvi_dynamic_plugin.AxMviState
import com.redwolf.mvi_dynamic_plugin.NoEffect
import kotlinx.coroutines.delay

data class HomeItem(val id: String, val title: String)


data class HomeAxMviState(
    val items: List<HomeItem> = emptyList(),
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val error: String? = null
) : AxMviState

/** 模拟服务端分页。TOTAL_ITEMS=87，pageSize 可变，最后一页可能不足 size。 */
class HomePagingRepository(private val totalItems: Int = 87) {
    suspend fun fetchPage(page: Int, pageSize: Int): List<HomeItem> {
// 模拟网络延迟
        delay(300)
        val start = (page - 1) * pageSize
        if (start >= totalItems) return emptyList()
        val endExclusive = minOf(totalItems, start + pageSize)
        return (start until endExclusive).map { idx ->
            HomeItem(id = "id_$idx", title = "标题 #$idx（第 ${page} 页）")
        }
    }
}
sealed class HomeAxMviAction : AxMviAction {
    data object OnAppear : HomeAxMviAction()
    data class LoadPage(val page: Int) : HomeAxMviAction()
    data class PageLoaded(val page: Int, val result: Result<List<HomeItem>>) : HomeAxMviAction()

}


class HomeReducer : Reducer<HomeAxMviState, HomeAxMviAction, NoEffect> {
    override fun reduce(state: HomeAxMviState, action: HomeAxMviAction): Pair<HomeAxMviState, Nothing?> = when(action){
        HomeAxMviAction.OnAppear -> state.copy(loading = true) to null
        is HomeAxMviAction.LoadPage -> state.copy(loading = true, error = null) to null
        is HomeAxMviAction.PageLoaded -> {
            val (page, r) = action
            r.fold(
                onSuccess = { list ->
                    val merged = if (page == 1) list else state.items + list
                    state.copy(items = merged, nextPage = page + 1, loading = false, error = null) to null
                },
                onFailure = { e -> state.copy(loading = false, error = e.message) to null }
            )
        }
    }
}