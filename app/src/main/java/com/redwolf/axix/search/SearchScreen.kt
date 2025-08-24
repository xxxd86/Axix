package com.redwolf.axix.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun SearchScreen(padding: PaddingValues, vm: SearchViewModel = viewModel()) {
    val state by vm.store.state.collectAsStateWithLifecycle()
    var tf by remember { mutableStateOf(TextFieldValue(state.query)) }


    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
        OutlinedTextField(
            value = tf,
            onValueChange = { tf = it; vm.onInput(it.text) },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth()
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.suggestions) { s -> ListItem(headlineContent = { Text(s) }); Divider() }
        }
    }
}