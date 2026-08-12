package com.dfrobot.rehab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.dfrobot.rehab.ui.AppNavHost
import com.dfrobot.rehab.ui.theme.RehabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RehabTheme {
                AppNavHost(navController = rememberNavController())
            }
        }
    }
}
