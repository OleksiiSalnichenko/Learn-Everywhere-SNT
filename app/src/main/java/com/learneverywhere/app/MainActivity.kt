package com.learneverywhere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.learneverywhere.app.ui.navigation.LearnEverywhereNavHost
import com.learneverywhere.app.ui.theme.LearnEverywhereTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LearnEverywhereTheme {
                LearnEverywhereNavHost()
            }
        }
    }
}
