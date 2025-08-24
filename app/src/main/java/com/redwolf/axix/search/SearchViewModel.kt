package com.redwolf.axix.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redwolf.mvi_dynamic_plugin.Store
import com.redwolf.mvi_dynamic_plugin.settings.AppServices
import com.redwolf.mvi_dynamic_plugin.traffic.DropStrategy
import com.redwolf.mvi_dynamic_plugin.traffic.NetType
import com.redwolf.mvi_dynamic_plugin.traffic.TrafficConfig
import com.redwolf.mvi_dynamic_plugin.traffic.TrafficMiddlewarePlus

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class SearchViewModel : ViewModel() {
    private val traffic = TrafficMiddlewarePlus<SearchAction>(
        configProvider = {
            TrafficConfig(
                maxConcurrent = 1,
                tokenCapacity = 10,
                tokensPerSecond = 6,
                dropStrategy = DropStrategy.COALESCE
            )
        },
        mapKey = { act -> if (act is SearchAction.Input) "search" else null },
        coalesceKey = { act -> if (act is SearchAction.Input) "search" else null },
        estimateBytes = { 6 * 1024 },
        edgeAdvisor = AppServices.edgeAdvisor,
        signalsProvider = { AppServices.signals },
        budgetManager = AppServices.byteBudget
    )


    val store = Store(
        initialState = SearchState(),
        reducer = SearchReducer(),
        middlewares = listOf(traffic),
        scope = viewModelScope
    ).also { it.start() }


    init {
// 输入 -> 去抖 -> 网络/离线 -> 回投
        viewModelScope.launch {
            store.state.map { it.query }
                .debounce(250)
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .collect { q ->
                    val offline = AppServices.signals.net == NetType.NONE
                    val result = runCatching { if (offline) localSuggest(q) else remoteSuggest(q) }
                    store.dispatch(SearchAction.SuggestionsLoaded(result))
                }
        }
    }


    fun onInput(text: String) = store.dispatch(SearchAction.Input(text))


    private suspend fun remoteSuggest(q: String): List<String> { delay(120); return List(6) { i -> "$q-sugg-$i" } }
    private fun localSuggest(q: String): List<String> = listOf("$q-离线-1","$q-离线-2","$q-离线-3")
}