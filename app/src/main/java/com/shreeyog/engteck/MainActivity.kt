package com.shreeyog.engteck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shreeyog.engteck.navigation.EngTeckNavGraph
import com.shreeyog.engteck.ui.theme.EngTeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EngTeckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EngTeckNavGraph()
                }
            }
        }
    }
}
