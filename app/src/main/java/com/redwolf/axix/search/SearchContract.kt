package com.redwolf.axix.search

import com.redwolf.mvi_dynamic_plugin.AxMviAction
import com.redwolf.mvi_dynamic_plugin.AxMviState
import com.redwolf.mvi_dynamic_plugin.NoEffect
import com.redwolf.mvi_dynamic_plugin.Reducer


data class SearchState(val query: String = "", val loading: Boolean = false, val suggestions: List<String> = emptyList()) : AxMviState


sealed class SearchAction : AxMviAction {
    data class Input(val text: String) : SearchAction()
    data class SuggestionsLoaded(val result: Result<List<String>>) : SearchAction()
}


class SearchReducer : Reducer<SearchState, SearchAction, NoEffect> {
    override fun reduce(state: SearchState, action: SearchAction): Pair<SearchState, Nothing?> = when(action){
        is SearchAction.Input -> state.copy(query = action.text, loading = true) to null
        is SearchAction.SuggestionsLoaded -> action.result.fold(
            onSuccess = { list -> state.copy(suggestions = list, loading = false) to null },
            onFailure = { _ -> state.copy(loading = false) to null }
        )
    }
}