package com.dfrobot.rehab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.dfrobot.rehab.ui.theme.RehabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RehabTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text("康复训练助手")
                }
            }
        }
    }
}
