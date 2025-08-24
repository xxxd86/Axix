package com.redwolf.axix.main


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.redwolf.axix.home.HomeScreen
import com.redwolf.axix.search.SearchScreen
import com.redwolf.axix.settings.DebugScreen
import com.redwolf.axix.settings.SettingsScreen


class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(0) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Edge‑Smart Traffic MVI") }) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(selected = tab==0, onClick={tab=0}, icon={ Icon(Icons.Default.Home,null)}, label={Text("Home")})
                            NavigationBarItem(selected = tab==1, onClick={tab=1}, icon={ Icon(Icons.Default.Search,null)}, label={Text("Search")})
                            NavigationBarItem(selected = tab==2, onClick={tab=2}, icon={ Icon(Icons.Default.Settings,null)}, label={Text("Settings")})
                            NavigationBarItem(selected = tab==3, onClick={tab=3}, icon={ Icon(Icons.Default.BugReport,null)}, label={Text("Debug")})
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> HomeScreen(padding)
                        1 -> SearchScreen(padding)
                        2 -> SettingsScreen(padding)
                        else -> DebugScreen(padding)
                    }
                }
            }
        }
    }
}