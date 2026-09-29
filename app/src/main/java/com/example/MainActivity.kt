package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.local.NexaDatabase
import com.example.data.repository.NexaRepository
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.NexaTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: NexaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NexaDatabase.getDatabase(applicationContext)
        repository = NexaRepository(database = database, context = applicationContext)

        setContent {
            NexaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(repository = repository)
                }
            }
        }
    }
}
