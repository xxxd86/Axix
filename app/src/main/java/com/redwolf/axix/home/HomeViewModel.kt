package com.redwolf.axix.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redwolf.mvi_dynamic_plugin.Store
import com.redwolf.mvi_dynamic_plugin.repo.MemoryCache
import com.redwolf.mvi_dynamic_plugin.repo.SWRRepository
import com.redwolf.mvi_dynamic_plugin.repo.SimpleDiskCache
import com.redwolf.mvi_dynamic_plugin.settings.AppServices
import com.redwolf.mvi_dynamic_plugin.traffic.TrafficConfig
import com.redwolf.mvi_dynamic_plugin.traffic.TrafficMiddlewarePlus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch


class HomeViewModel : ViewModel() {
    private val reducer = HomeReducer()


    private val traffic = TrafficMiddlewarePlus<HomeAxMviAction>(
        configProvider = { key ->
            when (key) {
                "feed" -> TrafficConfig(
                    maxConcurrent = 2,
                    tokenCapacity = 8,
                    tokensPerSecond = 4
                ); else -> TrafficConfig()
            }
        },
        mapKey = { act -> if (act is HomeAxMviAction.LoadPage) "feed" else null },
        coalesceKey = { act -> if (act is HomeAxMviAction.LoadPage) "feed" else null },
        estimateBytes = { 64 * 1024 }, // 估算每页字节
        edgeAdvisor = AppServices.edgeAdvisor,
        signalsProvider = { AppServices.signals },
        budgetManager = AppServices.byteBudget
    )


    val store = Store(
        initialState = HomeAxMviState(),
        reducer = reducer,
        middlewares = listOf(traffic),
        scope = viewModelScope
    ).also { it.start() }


    private val repo = SWRRepository<Pair<Int, Int>, List<HomeItem>>(
        mem = MemoryCache(),
        disk = SimpleDiskCache(),
        fetcher = { (page, size) -> fakeFetch(page, size) }
    )


    init {
        viewModelScope.launch {
            store.actions.filterIsInstance<HomeAxMviAction.LoadPage>()
                .collect { act ->
                    val size = AppServices.edgeDecision().pageSize ?: 20
                    val result = runCatching { repo.stream(act.page to size) }
// stream 返回 Flow<Result<List<HomeItem>>>，这里为了简单直接收集一次最新成功
                    result.getOrThrow().collect { r -> store.dispatch(HomeAxMviAction.PageLoaded(act.page, r)) }
                }
        }
    }


    fun onAppear() { store.dispatch(HomeAxMviAction.OnAppear); store.dispatch(HomeAxMviAction.LoadPage(1)) }
    fun loadNext() { store.dispatch(HomeAxMviAction.LoadPage(store.state.value.nextPage)) }


    // 模拟网络
    private suspend fun fakeFetch(page: Int, size: Int): List<HomeItem> {
        delay(120)
        return List(size) { i -> HomeItem("${page}_$i", "Title #$page-$i (size=$size)") }
    }
}