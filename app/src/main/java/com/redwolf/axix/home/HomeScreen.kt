package com.redwolf.axix.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun HomeScreen(padding: PaddingValues, vm: HomeViewModel = viewModel()) {
    val state by vm.store.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAppear() }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            last to total
        }
            .map { (last, total) -> total > 0 && last >= total - 5 }
            .distinctUntilChanged()
            .filter { it }
            .collect { vm.loadNext() }
    }

    Column(Modifier.padding(padding)) {
        if (state.error != null) Text(state.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.items) { idx, item ->
                ListItem(headlineContent = { Text(item.title) })
                Divider()
                //if (idx == state.items.lastIndex - 3) { SideEffect { vm.loadNext() } }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp)) }
        }
    }
}